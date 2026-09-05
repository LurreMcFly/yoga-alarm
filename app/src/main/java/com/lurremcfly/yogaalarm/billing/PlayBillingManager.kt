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
    val purchasing: Boolean = false,
    val activePlan: ProPlan? = null,
    val offers: Map<ProPlan, BillingOffer> = emptyMap(),
    val message: String? = null,
)

class PlayBillingManager(context: Context) : PurchasesUpdatedListener {
    private val mutableState = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = mutableState.asStateFlow()

    private val purchasesByType = mutableMapOf<String, List<Purchase>>()
    private var purchaseQueryFailed = false
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.value = mutableState.value.copy(connected = true, message = null)
                    queryOffers()
                    refresh()
                } else {
                    mutableState.value = mutableState.value.copy(
                        connected = false,
                        loading = false,
                        message = "Google Play purchases are unavailable right now.",
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.value = mutableState.value.copy(connected = false)
            }
        })
    }

    fun close() {
        billingClient.endConnection()
    }

    fun purchase(activity: Activity, plan: ProPlan) {
        val offer = mutableState.value.offers[plan]
        if (!billingClient.isReady || offer == null) {
            mutableState.value = mutableState.value.copy(message = "This plan is not available yet.")
            return
        }
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
        offer.offerToken?.let(productParamsBuilder::setOfferToken)
        val productParams = productParamsBuilder.build()
        mutableState.value = mutableState.value.copy(purchasing = true, message = null)
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.value = mutableState.value.copy(
                purchasing = false,
                message = billingMessage(result.responseCode),
            )
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            start()
            return
        }
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        refresh(showRestoreResult = true)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val completed = purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                completed.forEach(::acknowledge)
                val pending = purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }
                mutableState.value = mutableState.value.copy(
                    purchasing = false,
                    activePlan = planFor(completed.flatMap(Purchase::getProducts)) ?: mutableState.value.activePlan,
                    message = if (pending) "Purchase pending. Pro unlocks after Google Play confirms payment." else null,
                )
                refresh()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                mutableState.value = mutableState.value.copy(purchasing = false, message = null)
            }
            else -> {
                mutableState.value = mutableState.value.copy(
                    purchasing = false,
                    message = billingMessage(result.responseCode),
                )
            }
        }
    }

    private fun queryOffers() {
        ProPlan.entries.groupBy(ProPlan::productType).forEach { (productType, plans) ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    plans.map { plan ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(plan.productId)
                            .setProductType(productType)
                            .build()
                    },
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { result, queryResult ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
                val newOffers = queryResult.productDetailsList.mapNotNull(::offerFor).toMap()
                mutableState.value = mutableState.value.copy(offers = mutableState.value.offers + newOffers)
            }
        }
    }

    private fun offerFor(details: ProductDetails): Pair<ProPlan, BillingOffer>? {
        val plan = ProPlan.entries.firstOrNull { it.productId == details.productId } ?: return null
        val offer = if (plan.productType == BillingClient.ProductType.SUBS) {
            val subscription = details.subscriptionOfferDetails?.firstOrNull() ?: return null
            val price = subscription.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return null
            BillingOffer(price, details, subscription.offerToken)
        } else {
            val oneTime = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: details.oneTimePurchaseOfferDetails
                ?: return null
            BillingOffer(oneTime.formattedPrice, details, oneTime.offerToken)
        }
        return plan to offer
    }

    private fun refresh(showRestoreResult: Boolean = false) {
        purchasesByType.clear()
        purchaseQueryFailed = false
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { productType ->
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(productType).build(),
            ) { result, purchases ->
                val successful = result.responseCode == BillingClient.BillingResponseCode.OK
                purchaseQueryFailed = purchaseQueryFailed || !successful
                purchasesByType[productType] = if (successful) {
                    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                } else {
                    emptyList()
                }
                if (purchasesByType.size == 2) {
                    if (purchaseQueryFailed) {
                        mutableState.value = mutableState.value.copy(
                            loading = false,
                            purchasing = false,
                            message = "Google Play could not verify purchases. Try again when connected.",
                        )
                    } else {
                        applyPurchases(showRestoreResult)
                    }
                }
            }
        }
    }

    private fun applyPurchases(showRestoreResult: Boolean) {
        val purchases = purchasesByType.values.flatten()
        purchases.forEach(::acknowledge)
        val activePlan = planFor(purchases.flatMap(Purchase::getProducts))
        mutableState.value = mutableState.value.copy(
            connected = true,
            loading = false,
            purchasing = false,
            activePlan = activePlan,
            message = if (showRestoreResult) {
                if (activePlan == null) "No previous Pro purchase was found." else "Pro purchase restored."
            } else {
                null
            },
        )
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(
                    message = "Purchase received, but confirmation is still pending.",
                )
            }
        }
    }

    private fun planFor(productIds: List<String>): ProPlan? = when {
        ProPlan.LIFETIME.productId in productIds -> ProPlan.LIFETIME
        ProPlan.YEARLY.productId in productIds -> ProPlan.YEARLY
        ProPlan.MONTHLY.productId in productIds -> ProPlan.MONTHLY
        else -> null
    }

    private fun billingMessage(responseCode: Int): String = when (responseCode) {
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "You already own Pro. Try restoring purchases."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This plan is not available in your country yet."
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Check your connection and try again."
        else -> "Google Play could not complete the purchase. Please try again."
    }
}
