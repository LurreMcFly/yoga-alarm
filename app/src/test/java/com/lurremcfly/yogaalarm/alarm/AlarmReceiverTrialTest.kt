package com.lurremcfly.yogaalarm.alarm

import android.content.Context
import android.content.Intent
import com.lurremcfly.yogaalarm.billing.LastKnownPaidAccess
import com.lurremcfly.yogaalarm.billing.LastKnownPaidAccessCache
import com.lurremcfly.yogaalarm.billing.ProEntitlement
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.data.ProTrialStore
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmSound
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmReceiverTrialTest {
    private lateinit var context: Context
    private lateinit var original: AlarmConfig

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        listOf("yoga_alarm", "yoga_alarm_billing", "wake_progress").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        original = AlarmConfig.create(1).copy(sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        AlarmStore(context).save(listOf(original.copy(trialOriginal = original.customization)))
        context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit().putString("pro_preview_v1",
            """{"enrollment":"PREVIEW","dates":"2026-01-01,2026-01-02,2026-01-03,2026-01-04,2026-01-05","transition":"APPLIED"}""").commit()
    }

    private fun deliver() = AlarmReceiver().onReceive(context,
        Intent().putExtra(AlarmScheduler.EXTRA_ALARM_ID, original.id))

    @Test fun firstDeliveryKeepsLegacyRoutineUntilPlayConfirmsFreeOwnership() {
        deliver()
        val occurrence = AlarmOccurrenceStore(context).activeForAlarm(1)!!
        assertEquals(original.customization, occurrence.alarm.customization)
        assertEquals(original.customization, AlarmStore(context).load().single().customization)
        ProTrialStore(context).reconcile(paid = false, verifying = false, ownershipResolved = true)
        assertEquals(original.suggestedFreeFallback(), AlarmStore(context).load().single().customization)
        assertEquals(occurrence, AlarmOccurrenceStore(context).activeForAlarm(1))
    }

    @Test fun rememberedLifetimeAndExpiredOfflineSubscriptionPreventConversion() {
        val cache = LastKnownPaidAccessCache(context.getSharedPreferences("yoga_alarm_billing", Context.MODE_PRIVATE))
        for (plan in ProEntitlement.entries) {
            assertTrue(cache.save(LastKnownPaidAccess(plan, 0)))
            deliver()
            assertEquals(original.customization, AlarmStore(context).load().single().customization)
            assertEquals(original.customization, AlarmOccurrenceStore(context).activeForAlarm(1)!!.alarm.customization)
        }
    }

    @Test fun interruptedPurchaseAndSnoozedSnapshotRemainUnconverted() {
        val preferences = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
        val trial = org.json.JSONObject(preferences.getString("pro_preview_v1", null)!!)
        trial.put("awaitingPurchaseResolution", true)
        preferences.edit().putString("pro_preview_v1", trial.toString()).commit()
        deliver()
        val occurrences = AlarmOccurrenceStore(context)
        val occurrence = occurrences.activeForAlarm(1)!!
        assertTrue(occurrences.snooze(occurrence.token, 0))
        trial.put("awaitingPurchaseResolution", false)
        preferences.edit().putString("pro_preview_v1", trial.toString()).commit()
        AlarmReceiver().onReceive(context, Intent().putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
            .putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, true)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, occurrence.token))
        assertEquals(original.customization, occurrences.find(occurrence.token)!!.alarm.customization)
        assertEquals(original.customization, AlarmStore(context).load().single().customization)
    }
}
