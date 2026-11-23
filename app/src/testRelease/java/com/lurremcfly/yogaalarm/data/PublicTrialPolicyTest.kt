package com.lurremcfly.yogaalarm.data

import android.content.Context
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmSound
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublicTrialPolicyTest {
    @Test fun publicBuildIgnoresSeededOverridesAndRejectsMutations() {
        assertFalse(BuildConfig.DEBUG)
        val context = RuntimeEnvironment.getApplication() as Context
        val preferences = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val trials = ProTrialStore(context)
        val normal = trials.enroll(true, true)
        preferences.edit().putBoolean("pro_preview_internal_testing", true)
            .putBoolean("pro_preview_internal_paid", true)
            .putBoolean("pro_preview_internal_trial", true)
            .putBoolean("pro_preview_internal_expiry_banner", true)
            .putString("pro_preview_internal_trial_v1", "{}").commit()
        assertFalse(trials.internalTestingEnabled())
        assertFalse(trials.internalTestProEnabled())
        assertFalse(trials.internalTestTrialEnabled())
        assertFalse(trials.internalExpiryBannerEnabled())
        assertEquals(normal, trials.load())
        assertTrue(trials.hasPaidAccess(true))
        assertFalse(trials.hasPaidAccess(false))
        assertFalse(trials.setInternalTestingEnabled(false, { false }))
        assertFalse(trials.setInternalTestProEnabled(false, { false }))
        assertFalse(trials.setInternalTrialDaysRemaining(1, blocked = { false }))
        assertFalse(trials.setInternalExpiryBanner(false, { false }))
        assertFalse(trials.resetForInternalTesting({ false }, { false }))
        val draft = AlarmConfig.create(1).copy(sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        val saved = AlarmStore(context).saveEdited(draft, null, paid = false)!!
        assertEquals(draft.suggestedFreeFallback(), saved.trialFallback)
        assertEquals(normal, trials.reconcile(false, false))
        assertEquals(normal, ProTrialStore(context).load())
    }
}
