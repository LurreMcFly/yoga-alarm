package com.lurremcfly.yogaalarm.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.lurremcfly.yogaalarm.model.ProPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class PlayBillingManagerTest {
    private val ok = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()

    @Test fun suspendedPurchaseVerificationKeepsCheckoutBlocked() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val verification = CompletableDeferred<ProEntitlement?>()
        val manager = PlayBillingManager(RuntimeEnvironment.getApplication(), PurchaseVerifier { verification.await() })
        try {
            manager.onPurchasesUpdated(ok, listOf(purchase(0)))
            runCurrent()
            assertTrue(manager.state.value.verifyingPurchases)
            assertFalse(manager.state.value.canPurchase)
            val loadingBeforeRestore = manager.state.value.loading
            manager.restorePurchases()
            assertEquals(loadingBeforeRestore, manager.state.value.loading)
            assertTrue(manager.state.value.verifyingPurchases)
            manager.purchase(Activity(), ProPlan.LIFETIME)
            assertFalse(manager.state.value.purchasing)
            assertNull(manager.state.value.entitlement)
        } finally {
            manager.close()
            verification.complete(null)
            Dispatchers.resetMain()
        }
    }

    @Test fun canceledNonCooperativeVerifierCannotOverwriteNewPendingCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val verification = CompletableDeferred<ProEntitlement?>()
        val manager = PlayBillingManager(RuntimeEnvironment.getApplication(), PurchaseVerifier {
            withContext(NonCancellable) { verification.await() }
        })
        try {
            manager.onPurchasesUpdated(ok, listOf(purchase(0)))
            runCurrent()
            assertTrue(manager.state.value.verifyingPurchases)
            manager.onPurchasesUpdated(ok, listOf(purchase(4)))
            runCurrent()
            assertTrue(manager.state.value.pendingPurchase)
            verification.complete(ProEntitlement.SUBSCRIPTION)
            runCurrent()
            assertNull(manager.state.value.entitlement)
            assertTrue(manager.state.value.pendingPurchase)
            assertFalse(manager.state.value.canPurchase)
        } finally {
            verification.complete(null)
            manager.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun verifiedSubscriptionExpiresWhileManagerStaysOffline() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val grant = VerifiedPaidAccess(ProEntitlement.SUBSCRIPTION, "signed-test-proof", 2_000L)
        val verifier = object : RecoverablePurchaseVerifier {
            override suspend fun verifyRecoverable(purchase: PurchaseEvidence): VerifiedPaidAccess? = null
            override fun restoreCached(evidence: String, nowEpochMillis: Long) =
                grant.takeIf { evidence == it.cacheEvidence }
        }
        val preferences = context.getSharedPreferences("yoga_alarm_billing", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        assertTrue(PaidEntitlementCache(preferences, verifier).save(grant, 1_000L))
        var now = 1_000L
        val manager = PlayBillingManager(context, verifier) { now }
        try {
            assertEquals(ProEntitlement.SUBSCRIPTION, manager.state.value.entitlement)
            assertTrue(manager.state.value.recoverablePaidAccess)
            runCurrent()
            now = 2_000L
            advanceTimeBy(1_000L)
            runCurrent()
            assertNull(manager.state.value.entitlement)
            assertFalse(manager.state.value.recoverablePaidAccess)
            assertFalse(manager.state.value.canPurchase)
        } finally {
            manager.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun canceledCheckoutRemainsKnownWhenFollowupOwnershipQueryIsUnavailable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val manager = PlayBillingManager(RuntimeEnvironment.getApplication())
        try {
            manager.onPurchasesUpdated(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.USER_CANCELED).build(),
                null,
            )
            runCurrent()
            assertTrue(manager.state.value.checkoutCanceled)
            assertFalse(manager.state.value.verifyingPurchases)
            assertFalse(manager.state.value.canPurchase)
            assertNull(manager.state.value.entitlement)
            // The disconnected client's follow-up query cannot establish current ownership;
            // it must not erase the independently known canceled-checkout outcome.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runCurrent()
            assertTrue(manager.state.value.checkoutCanceled)
        } finally {
            manager.close()
            Dispatchers.resetMain()
        }
    }

    // Play purchase JSON encodes PURCHASED as 0 and PENDING as 4.
    private fun purchase(state: Int) = Purchase(
        """{"packageName":"com.lurremcfly.yogaalarm","productId":"yoga_alarm_pro","purchaseToken":"test-token","purchaseState":$state,"acknowledged":true}""",
        "",
    )
}
