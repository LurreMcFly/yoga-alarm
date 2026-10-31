package com.lurremcfly.yogaalarm.billing

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastKnownPaidAccessCacheTest {
    private val preferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("last_known_paid_cache_test", Context.MODE_PRIVATE)
    private val cache = LastKnownPaidAccessCache(preferences)
    private val checkedAt = 1_000_000_000L
    private val sevenDays = 7 * 24 * 60 * 60 * 1_000L

    @Before fun reset() {
        preferences.edit().clear().commit()
    }

    @Test fun subscriptionRoundTripPreservesOriginalCheckAndExpiresAtExactlySevenDays() {
        val access = LastKnownPaidAccess(ProEntitlement.SUBSCRIPTION, checkedAt)
        assertTrue(cache.save(access))
        val restarted = LastKnownPaidAccessCache(preferences)
        assertEquals(access, restarted.restore())
        assertTrue(restarted.restore()!!.isValidAt(checkedAt + sevenDays - 1))
        assertFalse(restarted.restore()!!.isValidAt(checkedAt + sevenDays))
        assertFalse(restarted.restore()!!.isValidAt(checkedAt + sevenDays + 1))
        assertEquals(access, restarted.restore())
    }

    @Test fun lifetimeSurvivesRestartWithoutSubscriptionDeadlineAndCanBeCleared() {
        val access = LastKnownPaidAccess(ProEntitlement.LIFETIME, checkedAt)
        assertTrue(cache.save(access))
        val restarted = LastKnownPaidAccessCache(preferences)
        assertEquals(access, restarted.restore())
        assertTrue(restarted.restore()!!.isValidAt(Long.MAX_VALUE))
        assertTrue(restarted.clear())
        assertNull(LastKnownPaidAccessCache(preferences).restore())
    }
}
