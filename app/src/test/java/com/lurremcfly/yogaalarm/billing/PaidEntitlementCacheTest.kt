package com.lurremcfly.yogaalarm.billing

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaidEntitlementCacheTest {
    private val preferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("paid_cache_test", Context.MODE_PRIVATE)
    private val grants = mutableMapOf<String, VerifiedPaidAccess>()
    private val verifier = object : RecoverablePurchaseVerifier {
        override suspend fun verifyRecoverable(purchase: PurchaseEvidence): VerifiedPaidAccess? = null
        override fun restoreCached(evidence: String, nowEpochMillis: Long) = grants[evidence]
    }
    private val cache = PaidEntitlementCache(preferences, verifier)
    private val subscription = VerifiedPaidAccess(ProEntitlement.SUBSCRIPTION, "authenticated-proof", 2_000L)

    @Before fun reset() {
        preferences.edit().clear().commit()
        grants.clear()
    }

    @Test fun durableVerifiedGrantRestoresAcrossCacheInstancesUntilExpiry() {
        grants[subscription.cacheEvidence] = subscription
        assertTrue(cache.save(subscription, 1_000L))
        val restarted = PaidEntitlementCache(preferences, verifier)
        assertEquals(subscription, restarted.restore(1_999L))
        assertNull(restarted.restore(2_000L))
    }

    @Test fun subscriptionWithoutTrustedExpiryCannotBeCached() {
        val grant = subscription.copy(validUntilEpochMillis = null)
        grants[grant.cacheEvidence] = grant
        assertFalse(cache.save(grant, 1_000L))
        assertNull(cache.restore(1_000L))
    }

    @Test fun tamperedOrRevokedEvidenceDoesNotRestore() {
        grants[subscription.cacheEvidence] = subscription
        assertTrue(cache.save(subscription, 1_000L))
        grants.clear()
        assertNull(cache.restore(1_000L))
        preferences.edit().putString("verified_paid_evidence", "invented-proof").commit()
        assertNull(cache.restore(1_000L))
    }

    @Test fun clientOnlyVerifierCannotActivateAnOfflineGrant() {
        preferences.edit().putString("verified_paid_evidence", subscription.cacheEvidence).commit()
        assertNull(PaidEntitlementCache(preferences, null).restore(1_000L))
        assertFalse(PaidEntitlementCache(preferences, null).save(subscription, 1_000L))
    }

    @Test fun lifetimeCanRestoreWithoutInventedExpiryAndClearRemovesOwnership() {
        val grant = VerifiedPaidAccess(ProEntitlement.LIFETIME, "lifetime-proof")
        grants[grant.cacheEvidence] = grant
        assertTrue(cache.save(grant, 1_000L))
        assertEquals(grant, cache.restore(Long.MAX_VALUE))
        assertTrue(cache.clear())
        assertNull(cache.restore(1_000L))
    }
}
