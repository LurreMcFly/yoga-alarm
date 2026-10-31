package com.lurremcfly.yogaalarm.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.data.ProTrialStore
import com.lurremcfly.yogaalarm.model.ProPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class PlayBillingLastKnownAccessTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("yoga_alarm_billing", Context.MODE_PRIVATE)
    private val cache = LastKnownPaidAccessCache(preferences)
    private val managers = mutableListOf<PlayBillingManager>()
    private val checkedAt = 1_000_000_000L
    private val sevenDays = 7 * 24 * 60 * 60 * 1_000L
    private var now = checkedAt
    private val ok = result(BillingClient.BillingResponseCode.OK)
    private val unavailable = result(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        preferences.edit().clear().commit()
        context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun cleanup() {
        preferences.edit().clear().commit()
        context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit().clear().commit()
        Dispatchers.resetMain()
    }

    @Test fun onlineLifetimeOwnershipRestoresOnOfflineColdStart() = billingTest {
        val online = manager(online = true) { type ->
            ok to if (type == BillingClient.ProductType.INAPP) listOf(purchase(ProPlan.LIFETIME)) else emptyList()
        }
        online.refresh()
        runCurrent()
        assertEquals(LastKnownPaidAccess(ProEntitlement.LIFETIME, checkedAt), cache.restore())
        assertTrue(online.state.value.recoverablePaidAccess)
        online.close()

        now += sevenDays * 20
        val offline = manager()
        assertEquals(ProEntitlement.LIFETIME, offline.state.value.entitlement)
        assertTrue(offline.state.value.recoverablePaidAccess)
        offline.refresh()
        runCurrent()
        assertEquals(ProEntitlement.LIFETIME, offline.state.value.entitlement)
        assertEquals(checkedAt, cache.restore()!!.lastOnlineCheckEpochMillis)
    }

    @Test fun offlineSubscriptionQueriesAndReopensNeverExtendOriginalSevenDayDeadline() = billingTest {
        val original = LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)
        assertTrue(cache.save(original))
        now += sevenDays - 1
        val offline = manager { ok to listOf(purchase(ProPlan.MONTHLY)) }
        assertEquals(ProEntitlement.SUBSCRIPTION, offline.state.value.entitlement)
        assertTrue(offline.state.value.recoverablePaidAccess)
        offline.refresh()
        runCurrent()
        offline.onPurchasesUpdated(ok, listOf(purchase(ProPlan.MONTHLY)))
        runCurrent()
        assertEquals(original, cache.restore())
        offline.close()

        now++
        val restarted = manager()
        assertNull(restarted.state.value.entitlement)
        assertFalse(restarted.state.value.recoverablePaidAccess)
        assertTrue(restarted.state.value.offlineAccessExpired)
        assertEquals(original, cache.restore())
    }

    @Test fun subscriptionExpiresAtExactBoundaryWhileOfflineManagerStaysOpen() = billingTest {
        assertTrue(cache.save(LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)))
        now += sevenDays - 1
        val offline = manager()
        runCurrent()
        assertEquals(ProEntitlement.SUBSCRIPTION, offline.state.value.entitlement)
        now++
        advanceTimeBy(1)
        runCurrent()
        assertNull(offline.state.value.entitlement)
        assertFalse(offline.state.value.recoverablePaidAccess)
        assertTrue(offline.state.value.offlineAccessExpired)
    }

    @Test fun partialOrFailedQueriesCannotClearOrRefreshSavedOwnership() = billingTest {
        for (failedType in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            val original = LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)
            assertTrue(cache.save(original))
            now = checkedAt + 1_000
            val billing = manager(online = true) { type ->
                if (type == failedType) unavailable to emptyList() else ok to emptyList()
            }
            billing.refresh()
            runCurrent()
            assertEquals(ProEntitlement.SUBSCRIPTION, billing.state.value.entitlement)
            assertEquals(OwnershipResolution.UNAVAILABLE, billing.state.value.ownershipResolution)
            assertEquals(original, cache.restore())
            billing.close()
        }
    }

    @Test fun replayedOnlineSubscriptionCallbackCannotRenewGraceWhenFollowupQueryIsPartial() = billingTest {
        val original = LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)
        assertTrue(cache.save(original))
        now += 1_000
        val billing = manager(online = true) { type ->
            if (type == BillingClient.ProductType.SUBS) ok to listOf(purchase(ProPlan.MONTHLY))
            else unavailable to emptyList()
        }
        billing.onPurchasesUpdated(ok, listOf(purchase(ProPlan.MONTHLY)))
        runCurrent()
        assertEquals(ProEntitlement.SUBSCRIPTION, billing.state.value.entitlement)
        assertEquals(OwnershipResolution.UNAVAILABLE, billing.state.value.ownershipResolution)
        assertEquals(original, cache.restore())
    }

    @Test fun pendingOwnershipCannotGrantOrReplacePaidCache() = billingTest {
        val original = LastKnownPaidAccess(ProEntitlement.LIFETIME, checkedAt)
        assertTrue(cache.save(original))
        now += 1_000
        val pending = manager(online = true) { type ->
            ok to if (type == BillingClient.ProductType.SUBS) listOf(purchase(ProPlan.MONTHLY, 4)) else emptyList()
        }
        pending.refresh()
        runCurrent()
        assertTrue(pending.state.value.pendingPurchase)
        assertEquals(ProEntitlement.LIFETIME, pending.state.value.entitlement)
        assertEquals(original, cache.restore())
        pending.close()

        assertTrue(cache.clear())
        val newCustomer = manager(online = true) { ok to listOf(purchase(ProPlan.MONTHLY, 4)) }
        newCustomer.refresh()
        runCurrent()
        assertNull(newCustomer.state.value.entitlement)
        assertFalse(newCustomer.state.value.recoverablePaidAccess)
        assertNull(cache.restore())
    }

    @Test fun losingValidatedNetworkBeforeQueryCompletesCannotRevokeLifetime() = billingTest {
        val original = LastKnownPaidAccess(ProEntitlement.LIFETIME, checkedAt)
        assertTrue(cache.save(original))
        var online = true
        val subscriptionQuery = CompletableDeferred<Pair<BillingResult, List<Purchase>>>()
        val billing = PlayBillingManager(
            context,
            hasValidatedNetwork = { online },
            queryPurchases = { type ->
                if (type == BillingClient.ProductType.SUBS) subscriptionQuery.await() else ok to emptyList()
            },
            nowEpochMillis = { now },
        ).also(managers::add)
        billing.refresh()
        runCurrent()
        online = false
        subscriptionQuery.complete(ok to emptyList())
        runCurrent()
        assertEquals(ProEntitlement.LIFETIME, billing.state.value.entitlement)
        assertEquals(original, cache.restore())
    }

    @Test fun rejectedKnownPurchaseCannotReplaceOrRenewSavedOwnership() = billingTest {
        val original = LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)
        assertTrue(cache.save(original))
        now += 1_000
        val billing = manager(online = true) { type ->
            ok to if (type == BillingClient.ProductType.INAPP) {
                listOf(purchase(ProPlan.LIFETIME, packageName = "another.package"))
            } else emptyList()
        }
        billing.refresh()
        runCurrent()
        assertEquals(ProEntitlement.SUBSCRIPTION, billing.state.value.entitlement)
        assertEquals(OwnershipResolution.UNAVAILABLE, billing.state.value.ownershipResolution)
        assertEquals(original, cache.restore())
    }

    @Test fun fullOnlineEmptySnapshotClearsSubscriptionAndRevokedLifetimeAcrossRestart() = billingTest {
        for (plan in ProEntitlement.entries) {
            assertTrue(cache.save(LastKnownPaidAccess(plan, checkedAt)))
            val online = manager(online = true) { ok to emptyList() }
            online.refresh()
            runCurrent()
            assertNull(online.state.value.entitlement)
            assertFalse(online.state.value.recoverablePaidAccess)
            assertEquals(OwnershipResolution.RESOLVED, online.state.value.ownershipResolution)
            assertNull(cache.restore())
            online.close()
            val restarted = manager()
            assertNull(restarted.state.value.entitlement)
            assertFalse(restarted.state.value.recoverablePaidAccess)
            restarted.close()
        }
    }

    @Test fun validatedOnlineSubscriptionSnapshotRenewsSavedCheckTime() = billingTest {
        assertTrue(cache.save(LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)))
        now += sevenDays
        val online = manager(online = true) { type ->
            ok to if (type == BillingClient.ProductType.SUBS) listOf(purchase(ProPlan.MONTHLY)) else emptyList()
        }
        assertNull(online.state.value.entitlement)
        online.refresh()
        runCurrent()
        assertEquals(ProEntitlement.SUBSCRIPTION, online.state.value.entitlement)
        assertTrue(online.state.value.recoverablePaidAccess)
        assertFalse(online.state.value.offlineAccessExpired)
        assertEquals(LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, now), cache.restore())
    }

    @Test fun canceledStaleQueryCannotEraseNewerLifetimeCallback() = billingTest {
        val staleSubscriptionQuery = CompletableDeferred<Pair<BillingResult, List<Purchase>>>()
        var subscriptionQueries = 0
        var lifetimeQueries = 0
        val billing = manager(online = true) { type ->
            when {
                type == BillingClient.ProductType.SUBS && subscriptionQueries++ == 0 ->
                    withContext(NonCancellable) { staleSubscriptionQuery.await() }
                type == BillingClient.ProductType.INAPP && lifetimeQueries++ > 0 ->
                    ok to listOf(purchase(ProPlan.LIFETIME))
                else -> ok to emptyList()
            }
        }
        billing.refresh()
        runCurrent()
        billing.onPurchasesUpdated(ok, listOf(purchase(ProPlan.LIFETIME)))
        runCurrent()
        assertEquals(ProEntitlement.LIFETIME, billing.state.value.entitlement)
        staleSubscriptionQuery.complete(ok to emptyList())
        runCurrent()
        assertEquals(ProEntitlement.LIFETIME, billing.state.value.entitlement)
        assertEquals(LastKnownPaidAccess(ProEntitlement.LIFETIME, now), cache.restore())
    }

    @Test fun cachedOwnershipRetiresPreviewDurablyAcrossRestart() = billingTest {
        val trials = ProTrialStore(context)
        trials.enroll(true, true)
        assertTrue(cache.save(LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)))
        val offline = manager()
        val state = offline.state.value
        assertTrue(trials.reconcile(
            paid = state.entitlement != null,
            verifying = state.verifyingPurchases,
            recoverablePaid = state.recoverablePaidAccess,
            ownershipResolved = state.ownershipResolution == OwnershipResolution.RESOLVED,
        ).retired)
        assertTrue(ProTrialStore(context).load().retired)
        now += sevenDays
        offline.close()
        val expired = manager()
        assertNull(expired.state.value.entitlement)
        assertTrue(ProTrialStore(context).reconcile(false, false, ownershipResolved = false).retired)
    }

    @Test fun internalNonPayingIgnoresGenuineCachedAccessWithoutChangingIt() = billingTest {
        assumeTrue(BuildConfig.PRO_TRIAL_INTERNAL_TESTING)
        val original = LastKnownPaidAccess(ProEntitlement.LIFETIME, checkedAt)
        assertTrue(cache.save(original))
        val trials = ProTrialStore(context)
        assertTrue(trials.setInternalNonPaying(true, { false }))
        assertTrue(trials.resetForInternalTesting({ true }, { false }))
        val offline = manager { ok to emptyList() }
        assertEquals(ProEntitlement.LIFETIME, offline.state.value.entitlement)
        assertFalse(trials.hasPaidAccess(offline.state.value.entitlement != null))
        assertFalse(trials.reconcile(true, false, recoverablePaid = offline.state.value.recoverablePaidAccess).retired)
        offline.refresh()
        runCurrent()
        assertEquals(original, cache.restore())
        offline.close()
        val restarted = manager()
        assertFalse(ProTrialStore(context).hasPaidAccess(restarted.state.value.entitlement != null))
        assertEquals(original, cache.restore())
        assertTrue(trials.setInternalNonPaying(false, { false }))
        assertTrue(trials.hasPaidAccess(restarted.state.value.entitlement != null))
    }

    private fun billingTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            managers.forEach(PlayBillingManager::close)
        }
    }

    private fun manager(
        online: Boolean = false,
        query: suspend (String) -> Pair<BillingResult, List<Purchase>> = { unavailable to emptyList() },
    ) = PlayBillingManager(
        context,
        hasValidatedNetwork = { online },
        queryPurchases = query,
        nowEpochMillis = { now },
    ).also(managers::add)

    // Play purchase JSON encodes PURCHASED as 0 and PENDING as 4.
    private fun purchase(plan: ProPlan, state: Int = 0, packageName: String = context.packageName) = Purchase(
        """{"packageName":"$packageName","productId":"${plan.productId}","purchaseToken":"test-${plan.name}","purchaseState":$state,"acknowledged":true}""",
        "",
    )

    private fun result(code: Int) = BillingResult.newBuilder().setResponseCode(code).build()
}
