package com.lurremcfly.yogaalarm.billing

import com.android.billingclient.api.Purchase
import com.lurremcfly.yogaalarm.model.ProPlan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseVerifierTest {
    private val verifier = ClientPurchaseVerifier("com.lurremcfly.yogaalarm")
    private val subscription = PurchaseEvidence(
        "com.lurremcfly.yogaalarm", listOf("yoga_alarm_pro"), "test-token",
        Purchase.PurchaseState.PURCHASED,
    )

    @Test fun subscriptionRestoresWithoutKnowingItsBasePlan() = runTest {
        assertEquals(ProEntitlement.SUBSCRIPTION, verifier.verify(subscription))
    }

    @Test fun lifetimeRestores() = runTest {
        assertEquals(
            ProEntitlement.LIFETIME,
            verifier.verify(subscription.copy(productIds = listOf("yoga_alarm_pro_lifetime"))),
        )
    }

    @Test fun pendingSubscriptionAndLifetimeNeverGrantPro() = runTest {
        for (id in listOf("yoga_alarm_pro", "yoga_alarm_pro_lifetime")) {
            assertNull(verifier.verify(subscription.copy(productIds = listOf(id), purchaseState = Purchase.PurchaseState.PENDING)))
        }
    }

    @Test fun unspecifiedStateNeverGrantsPro() = runTest {
        assertNull(verifier.verify(subscription.copy(purchaseState = Purchase.PurchaseState.UNSPECIFIED_STATE)))
    }

    @Test fun wrongPackageOrEmptyTokenIsRejected() = runTest {
        assertNull(verifier.verify(subscription.copy(packageName = "another.app")))
        assertNull(verifier.verify(subscription.copy(purchaseToken = "")))
    }

    @Test fun prototypeAndUnknownProductsDoNotGrantPro() = runTest {
        for (id in listOf("pro_monthly", "pro_yearly", "pro_lifetime", "unknown")) {
            assertNull(verifier.verify(subscription.copy(productIds = listOf(id))))
        }
        assertNull(verifier.verify(subscription.copy(productIds = emptyList())))
    }

    @Test fun lifetimeTakesPrecedence() = runTest {
        assertEquals(
            ProEntitlement.LIFETIME,
            verifier.verify(subscription.copy(productIds = listOf("yoga_alarm_pro", "yoga_alarm_pro_lifetime"))),
        )
    }

    @Test fun subscriptionChoicesShareProductButUseDifferentBasePlans() {
        assertEquals("yoga_alarm_pro", ProPlan.MONTHLY.productId)
        assertEquals(ProPlan.MONTHLY.productId, ProPlan.YEARLY.productId)
        assertEquals("monthly", ProPlan.MONTHLY.basePlanId)
        assertEquals("yearly", ProPlan.YEARLY.basePlanId)
        assertEquals("yoga_alarm_pro_lifetime", ProPlan.LIFETIME.productId)
    }
}
