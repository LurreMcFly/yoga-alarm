package com.lurremcfly.yogaalarm.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.lurremcfly.yogaalarm.model.ProPlan
import com.lurremcfly.yogaalarm.analytics.MonetizationAnalytics
import com.lurremcfly.yogaalarm.data.AlarmStorageLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class BillingOffer(
    val formattedPrice: String,
    internal val productDetails: ProductDetails,
    internal val offerToken: String?,
)

enum class OwnershipResolution { LOADING, RESOLVED, UNAVAILABLE }

data class BillingUiState(
    val connected: Boolean = false,
    val loading: Boolean = true,
    val loadingOffers: Boolean = true,
    val purchasing: Boolean = false,
    val ownershipResolution: OwnershipResolution = OwnershipResolution.LOADING,
    val verifyingPurchases: Boolean = false,
    val pendingPurchase: Boolean = false,
    val checkoutCanceled: Boolean = false,
    val catalogSettled: Boolean = false,
    val recoverablePaidAccess: Boolean = false,
    val entitlement: ProEntitlement? = null,
    val subscriptionAutoRenewing: Boolean? = null,
    val offers: Map<ProPlan, BillingOffer> = emptyMap(),
    val message: String? = null,
) {
    val canPurchase: Boolean
        get() = connected && catalogSettled && !loadingOffers && !loading &&
            ownershipResolution == OwnershipResolution.RESOLVED && !purchasing &&
            !verifyingPurchases && !pendingPurchase && entitlement == null
}

class PlayBillingManager(
    context: Context,
    private val verifier: PurchaseVerifier = ClientPurchaseVerifier(context.packageName),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : PurchasesUpdatedListener {
    private val analytics = MonetizationAnalytics(context.applicationContext)
    private val paidCache = PaidEntitlementCache(
        context.applicationContext.getSharedPreferences("yoga_alarm_billing", Context.MODE_PRIVATE),
        verifier as? RecoverablePurchaseVerifier,
    )
    private val cachedAccess = paidCache.restore(nowEpochMillis())
    private var verifiedAccess = cachedAccess
    private val mutableState = MutableStateFlow(BillingUiState(
        entitlement = cachedAccess?.entitlement,
        recoverablePaidAccess = cachedAccess != null,
    ))
    val state: StateFlow<BillingUiState> = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connecting = false
    private var closed = false
    private var restoreRequested = false
    private var retryDelay = 1_000L
    private var retryJob: Job? = null
    private var refreshJob: Job? = null
    private var offersJob: Job? = null
    private var purchaseJob: Job? = null
    private var paidExpiryJob: Job? = null
    private val acknowledging = mutableSetOf<String>()
    private val acknowledged = mutableSetOf<String>()
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        schedulePaidExpiry()
    }

    fun start() {
        if (closed) return
        expireKnownPaidAccess()
        schedulePaidExpiry()
        if (connecting) return
        if (billingClient.isReady) {
            mutableState.value = mutableState.value.copy(connected = true)
            queryOffers()
            if (purchaseJob?.isActive != true) refresh()
            return
        }
        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                scope.launch {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        retryJob?.cancel()
                        retryDelay = 1_000L
                        mutableState.value = mutableState.value.copy(connected = true, message = null)
                        queryOffers()
                        if (purchaseJob?.isActive != true) refresh()
                    } else {
                        mutableState.value = mutableState.value.copy(
                            connected = false, loading = false, loadingOffers = false,
                            ownershipResolution = OwnershipResolution.UNAVAILABLE,
                            catalogSettled = true,
                            message = "Google Play purchases are unavailable. Tap Retry to reconnect.",
                        )
                        if (isRetryable(result.responseCode)) scheduleRetry()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                scope.launch {
                    connecting = false
                    mutableState.value = mutableState.value.copy(connected = false)
                    scheduleRetry()
                }
            }
        })
    }

    private fun schedulePaidExpiry() {
        paidExpiryJob?.cancel()
        if (verifiedAccess?.validUntilEpochMillis == null || closed) return
        paidExpiryJob = scope.launch {
            while (true) {
                val deadline = verifiedAccess?.validUntilEpochMillis ?: return@launch
                val remaining = deadline - nowEpochMillis()
                if (remaining > 0) {
                    // Bound the clock recheck so a wall-clock change cannot leave a stale grant
                    // alive until a long monotonic delay finishes.
                    delay(remaining.coerceAtMost(60_000L))
                } else {
                    expireKnownPaidAccess()
                    if (billingClient.isReady && !state.value.verifyingPurchases) refresh()
                    if (verifiedAccess?.validUntilEpochMillis == null) return@launch
                }
            }
        }
    }

    private fun expireKnownPaidAccess() = synchronized(AlarmStorageLock) {
        val previous = verifiedAccess ?: return@synchronized
        val now = nowEpochMillis()
        if (previous.isValidAt(now)) return@synchronized
        // Only a known verified validity boundary removes access. Query failures carry no
        // expiry information. A newer authenticated proof can supersede the expired grant.
        val restored = paidCache.restore(now)
        verifiedAccess = restored
        if (restored == null) paidCache.clear()
        mutableState.value = state.value.copy(
            entitlement = if (state.value.entitlement == previous.entitlement) restored?.entitlement else state.value.entitlement,
            recoverablePaidAccess = restored != null,
            ownershipResolution = if (restored == null) OwnershipResolution.UNAVAILABLE else state.value.ownershipResolution,
            subscriptionAutoRenewing = if (restored?.entitlement == ProEntitlement.SUBSCRIPTION) state.value.subscriptionAutoRenewing else null,
        )
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true || closed) return
        retryJob = scope.launch {
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(60_000L)
            retryJob = null
            start()
        }
    }

    fun close() {
        closed = true
        scope.cancel()
        billingClient.endConnection()
    }

    fun purchase(activity: Activity, plan: ProPlan) {
        if (closed || !state.value.canPurchase) return
        val offer = state.value.offers[plan]
        if (!billingClient.isReady || offer == null) {
            mutableState.value = state.value.copy(message = "This plan is unavailable. Retrying Google Play…")
            start()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
        offer.offerToken?.let(productParams::setOfferToken)
        mutableState.value = state.value.copy(purchasing = true, checkoutCanceled = false, message = null)
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onPurchasesUpdated(result, null)
        } else {
            analytics.record("checkout_started", mapOf("plan" to plan.name.lowercase()))
        }
    }

    fun restorePurchases() {
        restoreRequested = true
        if (purchaseJob?.isActive == true) {
            // Its completion already starts a fresh ownership query. Keep the verification
            // guard and avoid a second, independently managed loading state.
            mutableState.value = state.value.copy(message = null)
            return
        }
        mutableState.value = state.value.copy(loading = true, message = null)
        start()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        scope.launch {
            refreshJob?.cancel() // A snapshot begun before checkout must not overwrite its result.
            purchaseJob?.cancel()
            mutableState.value = state.value.copy(
                purchasing = false,
                verifyingPurchases = result.responseCode == BillingClient.BillingResponseCode.OK,
                checkoutCanceled = result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED,
                ownershipResolution = OwnershipResolution.LOADING,
            )
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchaseJob = scope.launch {
                        try {
                            applyPurchases(purchases.orEmpty(), replace = false)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            mutableState.value = state.value.copy(
                                verifyingPurchases = false,
                                ownershipResolution = OwnershipResolution.UNAVAILABLE,
                                message = "Could not verify purchase. Please restore purchases.",
                            )
                        }
                        refresh()
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    analytics.record("checkout_outcome", mapOf("outcome" to "canceled"))
                    mutableState.value = state.value.copy(message = null)
                    refresh()
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restorePurchases()
                else -> {
                    analytics.record("checkout_outcome", mapOf("outcome" to "failed"))
                    mutableState.value = state.value.copy(message = billingMessage(result.responseCode))
                    queryOffers() // Discard potentially stale ProductDetails after a failed checkout.
                    if (isRetryable(result.responseCode)) scheduleRetry()
                    refresh()
                }
            }
        }
    }

    private fun queryOffers() {
        offersJob?.cancel()
        offersJob = scope.launch {
            // Never leave a failed or removed product purchasable using stale details.
            mutableState.value = state.value.copy(loadingOffers = true)
            val settledOffers = mutableMapOf<ProPlan, BillingOffer>()
            ProPlan.entries.groupBy(ProPlan::productType).forEach { (type, plans) ->
                val params = QueryProductDetailsParams.newBuilder().setProductList(
                    plans.distinctBy(ProPlan::productId).map {
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(it.productId).setProductType(type).build()
                    },
                ).build()
                val (result, details) = suspendCancellableCoroutine { continuation ->
                    billingClient.queryProductDetailsAsync(params) { result, products ->
                        if (continuation.isActive) continuation.resume(result to products.productDetailsList)
                    }
                }
                val offers = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    details.flatMap(::offersFor).toMap()
                } else emptyMap()
                settledOffers.putAll(offers)
                if (result.responseCode != BillingClient.BillingResponseCode.OK || offers.size != plans.size) {
                    if (isRetryable(result.responseCode)) scheduleRetry()
                }
            }
            mutableState.value = state.value.copy(
                offers = settledOffers.toMap(), loadingOffers = false, catalogSettled = true,
            )
        }
    }

    private fun offersFor(details: ProductDetails): List<Pair<ProPlan, BillingOffer>> =
        ProPlan.entries.filter { it.productId == details.productId && it.productType == details.productType }
            .mapNotNull { plan ->
                val offer = if (plan.basePlanId != null) {
                    // Launch the full-price base plan, never an arbitrary trial/intro offer.
                    val subscription = details.subscriptionOfferDetails?.firstOrNull {
                        it.basePlanId == plan.basePlanId && it.offerId == null
                    } ?: return@mapNotNull null
                    val price = subscription.pricingPhases.pricingPhaseList.singleOrNull()
                        ?.formattedPrice ?: return@mapNotNull null
                    BillingOffer(price, details, subscription.offerToken)
                } else {
                    val oneTime = details.oneTimePurchaseOfferDetailsList?.firstOrNull {
                        it.purchaseOptionId == "yogaalarmprolifetime" && it.offerId == null
                    } ?: return@mapNotNull null
                    BillingOffer(oneTime.formattedPrice, details, oneTime.offerToken)
                }
                plan to offer
            }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutableState.value = state.value.copy(
                loading = true, ownershipResolution = OwnershipResolution.LOADING,
            )
            val queries = listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).map { type ->
                async {
                    suspendCancellableCoroutine { continuation ->
                        billingClient.queryPurchasesAsync(
                            QueryPurchasesParams.newBuilder().setProductType(type).build(),
                        ) { result, purchases ->
                            if (continuation.isActive) continuation.resume(result to purchases)
                        }
                    }
                }
            }
            val results = queries.map { it.await() }
            val failure = results.firstOrNull { it.first.responseCode != BillingClient.BillingResponseCode.OK }
            if (failure != null) {
                mutableState.value = state.value.copy(
                    loading = false,
                    verifyingPurchases = false,
                    ownershipResolution = OwnershipResolution.UNAVAILABLE,
                    message = "Google Play could not check purchases. Tap Restore purchases to retry.",
                )
                if (isRetryable(failure.first.responseCode)) scheduleRetry()
                return@launch
            }
            try {
                mutableState.value = state.value.copy(verifyingPurchases = true)
                applyPurchases(results.flatMap { it.second }, replace = true)
                restoreRequested = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(
                    loading = false, verifyingPurchases = false,
                    ownershipResolution = OwnershipResolution.UNAVAILABLE,
                    message = "Could not verify purchases. Please try restoring again.",
                )
                scheduleRetry()
            }
        }
    }

    private suspend fun applyPurchases(purchases: List<Purchase>, replace: Boolean) {
        var entitlement = if (replace) null else state.value.entitlement
        var subscriptionAutoRenewing = if (replace) null else state.value.subscriptionAutoRenewing
        var recoverableAccess = if (replace) null else paidCache.restore(nowEpochMillis())
        val accepted = mutableListOf<Purchase>()
        for (purchase in purchases.distinctBy(Purchase::getPurchaseToken)) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val evidence = PurchaseEvidence(
                purchase.packageName, purchase.products, purchase.purchaseToken, purchase.purchaseState,
            )
            val recoverable = (verifier as? RecoverablePurchaseVerifier)?.verifyRecoverable(evidence)
            val verified = if (verifier is RecoverablePurchaseVerifier) {
                recoverable?.takeIf { it.isValidAt(nowEpochMillis()) }?.entitlement
            } else verifier.verify(evidence)
            if (verified == null) continue
            if (recoverable != null && recoverableAccess?.entitlement != ProEntitlement.LIFETIME) {
                recoverableAccess = recoverable
            }
            accepted += purchase
            if (verified == ProEntitlement.SUBSCRIPTION) {
                subscriptionAutoRenewing = purchase.isAutoRenewing
            }
            if (entitlement != ProEntitlement.LIFETIME) entitlement = verified
        }
        val pending = purchases.any {
            it.purchaseState == Purchase.PurchaseState.PENDING &&
                it.products.any { id -> id == ProPlan.MONTHLY.productId || id == ProPlan.LIFETIME.productId }
        }
        // A verifier can finish after cancellation. Never let an obsolete ownership snapshot
        // overwrite a purchase callback or its durable proof.
        coroutineContext.ensureActive()
        synchronized(AlarmStorageLock) {
            val recoverablePaidAccess = if (entitlement != null && recoverableAccess != null && recoverableAccess.entitlement == entitlement) {
                paidCache.save(recoverableAccess, nowEpochMillis())
            } else {
                if (replace) paidCache.clear()
                false
            }
            verifiedAccess = recoverableAccess?.takeIf { it.entitlement == entitlement }
            mutableState.value = state.value.copy(
                loading = false,
                ownershipResolution = if (replace) OwnershipResolution.RESOLVED else state.value.ownershipResolution,
                verifyingPurchases = false,
                pendingPurchase = pending || (!replace && state.value.pendingPurchase),
                recoverablePaidAccess = recoverablePaidAccess,
                entitlement = entitlement,
                subscriptionAutoRenewing = subscriptionAutoRenewing,
                message = when {
                    pending -> "Purchase pending. Pro unlocks after Google Play confirms payment."
                    replace && restoreRequested -> if (entitlement == null) "No previous Pro purchase was found." else "Pro purchase restored."
                    else -> state.value.message?.takeUnless { it.startsWith("Purchase pending.") }
                },
            )
        }
        schedulePaidExpiry()
        if (!replace) {
            purchases.filter { purchase ->
                purchase.products.any { it == ProPlan.MONTHLY.productId || it == ProPlan.LIFETIME.productId }
            }.forEach { purchase ->
                val outcome = when {
                    purchase in accepted -> "purchased"
                    purchase.purchaseState == Purchase.PurchaseState.PENDING -> "pending"
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED -> "failed"
                    else -> return@forEach
                }
                // Deduplicate callback replay locally without storing or transmitting purchase
                // secrets as analytics identifiers. This remains a provisional funnel signal.
                val localKey = MessageDigest.getInstance("SHA-256")
                    .digest(purchase.purchaseToken.toByteArray()).joinToString("") { "%02x".format(it) }
                analytics.recordOnce("checkout_outcome", "$localKey:$outcome", mapOf("outcome" to outcome))
            }
        } else if (restoreRequested && entitlement != null) {
            analytics.record("purchase_restored")
        }
        accepted.forEach(::acknowledge)
    }

    private fun acknowledge(purchase: Purchase) {
        val token = purchase.purchaseToken
        if (purchase.isAcknowledged || token in acknowledged || !acknowledging.add(token)) return
        scope.launch {
            try {
                var delayMillis = 1_000L
                while (true) {
                    val result = suspendCancellableCoroutine { continuation ->
                        billingClient.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
                        ) { result -> if (continuation.isActive) continuation.resume(result) }
                    }
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        acknowledged += token
                        break
                    }
                    if (result.responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
                        refresh()
                        break
                    }
                    mutableState.value = state.value.copy(message = "Purchase received. Google Play confirmation will be retried.")
                    if (!isRetryable(result.responseCode)) break
                    delay(delayMillis)
                    delayMillis = (delayMillis * 2).coerceAtMost(60_000L)
                }
            } finally {
                acknowledging.remove(token)
            }
        }
    }

    private fun isRetryable(code: Int): Boolean = code in listOf(
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.ERROR,
    )

    private fun billingMessage(responseCode: Int): String = when (responseCode) {
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This plan is not available in your country yet."
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Check your connection and try again."
        else -> "Google Play could not complete the purchase. Please try again."
    }
}
