package com.lurremcfly.yogaalarm.billing

import com.android.billingclient.api.Purchase
import com.lurremcfly.yogaalarm.model.ProPlan

enum class ProEntitlement(val displayName: String) {
    SUBSCRIPTION("Subscription"),
    LIFETIME("Lifetime"),
}

// Only billing data crosses this boundary. Never log purchase tokens.
data class PurchaseEvidence(
    val packageName: String,
    val productIds: List<String>,
    val purchaseToken: String,
    val purchaseState: Int,
)

fun interface PurchaseVerifier {
    // A backend implementation must verify with Google, not trust these client fields.
    // Return null for rejected/expired purchases; throw on a temporary verification outage.
    suspend fun verify(purchase: PurchaseEvidence): ProEntitlement?
}

/** Interim client-only trust policy, NOT cryptographic or server-side verification. */
class ClientPurchaseVerifier(private val packageName: String) : PurchaseVerifier {
    override suspend fun verify(purchase: PurchaseEvidence): ProEntitlement? {
        if (purchase.packageName != packageName || purchase.purchaseToken.isBlank() ||
            purchase.purchaseState != Purchase.PurchaseState.PURCHASED
        ) return null
        return when {
            ProPlan.LIFETIME.productId in purchase.productIds -> ProEntitlement.LIFETIME
            ProPlan.MONTHLY.productId in purchase.productIds -> ProEntitlement.SUBSCRIPTION
            else -> null
        }
    }
}
