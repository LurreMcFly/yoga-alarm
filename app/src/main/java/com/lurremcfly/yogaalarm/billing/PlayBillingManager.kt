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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BillingOffer(
    val formattedPrice: String,
    internal val productDetails: ProductDetails,
    internal val offerToken: String?,
)

data class BillingUiState(
    val connected: Boolean = false,
    val loading: Boolean = true,
    val loadingOffers: Boolean = true,
    val purchasing: Boolean = false,
    val entitlement: ProEntitlement? = null,
    val subscriptionAutoRenewing: Boolean? = null,
    val offers: Map<ProPlan, BillingOffer> = emptyMap(),
    val message: String? = null,
)

class PlayBillingManager(
    context: Context,
    private val verifier: PurchaseVerifier = ClientPurchaseVerifier(context.packageName),
) : PurchasesUpdatedListener {
    private val mutableState = MutableStateFlow(BillingUiState())
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
    private val acknowledging = mutableSetOf<String>()
    private val acknowledged = mutableSetOf<String>()
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (closed || connecting) return
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
                        refresh()
                    } else {
                        mutableState.value = mutableState.value.copy(
                            connected = false, loading = false, loadingOffers = false,
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
        if (closed || state.value.purchasing || state.value.entitlement != null) return
        val offer = state.value.offers[plan]
        if (!billingClient.isReady || offer == null) {
            mutableState.value = state.value.copy(message = "This plan is unavailable. Retrying Google Play…")
            start()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
        offer.offerToken?.let(productParams::setOfferToken)
        mutableState.value = state.value.copy(purchasing = true, message = null)
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onPurchasesUpdated(result, null)
        }
    }

    fun restorePurchases() {
        restoreRequested = true
        mutableState.value = state.value.copy(loading = true, message = null)
        start()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        scope.launch {
            refreshJob?.cancel() // A snapshot begun before checkout must not overwrite its result.
            purchaseJob?.cancel()
            mutableState.value = state.value.copy(purchasing = false)
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchaseJob = scope.launch {
                        try {
                            applyPurchases(purchases.orEmpty(), replace = false)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            mutableState.value = state.value.copy(message = "Could not verify purchase. Please restore purchases.")
                        }
                        refresh()
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    mutableState.value = state.value.copy(message = null)
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restorePurchases()
                else -> {
                    mutableState.value = state.value.copy(message = billingMessage(result.responseCode))
                    queryOffers() // Discard potentially stale ProductDetails after a failed checkout.
                    if (isRetryable(result.responseCode)) scheduleRetry()
                }
            }
        }
    }

    private fun queryOffers() {
        offersJob?.cancel()
        offersJob = scope.launch {
            // Never leave a failed or removed product purchasable using stale details.
            mutableState.value = state.value.copy(offers = emptyMap(), loadingOffers = true)
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
                val offers = details.flatMap(::offersFor).toMap()
                mutableState.value = state.value.copy(offers = state.value.offers + offers)
                if (result.responseCode != BillingClient.BillingResponseCode.OK || offers.size != plans.size) {
                    if (isRetryable(result.responseCode)) scheduleRetry()
                }
            }
            mutableState.value = state.value.copy(loadingOffers = false)
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
                    message = "Google Play could not check purchases. Tap Restore purchases to retry.",
                )
                if (isRetryable(failure.first.responseCode)) scheduleRetry()
                return@launch
            }
            try {
                applyPurchases(results.flatMap { it.second }, replace = true)
                restoreRequested = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(loading = false, message = "Could not verify purchases. Please try restoring again.")
                scheduleRetry()
            }
        }
    }

    private suspend fun applyPurchases(purchases: List<Purchase>, replace: Boolean) {
        var entitlement = if (replace) null else state.value.entitlement
        var subscriptionAutoRenewing = if (replace) null else state.value.subscriptionAutoRenewing
        val accepted = mutableListOf<Purchase>()
        for (purchase in purchases.distinctBy(Purchase::getPurchaseToken)) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val verified = verifier.verify(
                PurchaseEvidence(purchase.packageName, purchase.products, purchase.purchaseToken, purchase.purchaseState),
            ) ?: continue
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
        mutableState.value = state.value.copy(
            loading = false,
            entitlement = entitlement,
            subscriptionAutoRenewing = subscriptionAutoRenewing,
            message = when {
                pending -> "Purchase pending. Pro unlocks after Google Play confirms payment."
                replace && restoreRequested -> if (entitlement == null) "No previous Pro purchase was found." else "Pro purchase restored."
                else -> state.value.message?.takeUnless { it.startsWith("Purchase pending.") }
            },
        )
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
