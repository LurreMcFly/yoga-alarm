package com.lurremcfly.yogaalarm.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.lurremcfly.yogaalarm.model.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProTrialStoreTest {
    private lateinit var context: Context
    private lateinit var trials: ProTrialStore
    private lateinit var alarms: AlarmStore
    private lateinit var occurrences: AlarmOccurrenceStore
    private val first = ZonedDateTime.parse("2026-10-01T23:59:59+02:00[Europe/Stockholm]")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        listOf("yoga_alarm", "onboarding", "wake_progress").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        trials = ProTrialStore(context)
        alarms = AlarmStore(context)
        occurrences = AlarmOccurrenceStore(context)
    }

    @Test fun assignmentPrecedesDefaultWritesAndRemainsStableAfterRestart() {
        assertEquals(TrialEnrollment.PREVIEW, trials.enroll(true, true).enrollment)
        WakeProgressStore(context).load(first)
        alarms.save(listOf(AlarmConfig.create(1)))
        assertEquals(TrialEnrollment.PREVIEW, ProTrialStore(context).enroll(false, false).enrollment)
    }

    @Test fun existingAlarmOnboardingOrWakeDataExcludesEnrollment() {
        for (name in listOf("yoga_alarm", "onboarding", "wake_progress")) {
            listOf("yoga_alarm", "onboarding", "wake_progress").forEach {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString(if (name == "yoga_alarm") "alarms" else "existing", "").commit()
            assertEquals(name, TrialEnrollment.INELIGIBLE, ProTrialStore(context).enroll(true, true).enrollment)
        }
    }

    @Test fun duplicateOccurrenceReplayAfterMidnightAndTimezoneChangeKeepsOriginalDate() {
        trials.enroll(true, true)
        val alarm = AlarmConfig.create(1)
        alarms.save(listOf(alarm))
        val occurrence = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(occurrence.token, first, { false }, { false }))
        assertTrue(ProTrialStore(context).complete(occurrence.token, first.plusHours(30).withZoneSameInstant(java.time.ZoneId.of("Pacific/Auckland")), { false }, { false }))
        assertEquals(setOf(first.toLocalDate()), trials.load().dates)
        assertEquals(first, occurrences.find(occurrence.token)?.completedAt)
        complete(alarm, first.plusMinutes(1))
        assertEquals(2, trials.load().dates.size)
    }

    @Test fun fifthCompletionConvertsLatestAlarmsAtomicallyAndPreservesSnoozedSnapshot() {
        trials.enroll(true, true)
        val paid = customized(1)
        val disabled = customized(2).copy(enabled = false, weekdays = emptySet())
        val deleted = customized(3)
        alarms.save(listOf(paid, deleted, disabled))
        repeat(4) { complete(paid, first.plusDays(it * 3L)) }
        val snoozed = occurrences.begin(disabled, 2)
        assertTrue(occurrences.snooze(snoozed.token, 1))
        alarms.update { it.filterNot { alarm -> alarm.id == deleted.id } }
        alarms.reorder(listOf(disabled.id, paid.id, deleted.id))
        complete(paid, first.plusDays(20))
        val current = AlarmStore(context).load()
        assertEquals(listOf(2L, 1L), current.map { it.id })
        assertFalse(current.first().enabled)
        assertEquals(emptySet<Int>(), current.first().weekdays)
        for (alarm in current) {
            assertEquals(alarm.trialFallback, alarm.customization)
            assertEquals(paid.customization, alarm.trialOriginal)
        }
        assertEquals(FreeTransition.APPLIED, ProTrialStore(context).load().transition)
        assertEquals(disabled, AlarmOccurrenceStore(context).find(snoozed.token)?.alarm)
        trials.reconcile(false, false)
        assertEquals(current, alarms.load())
    }

    @Test fun restorationRequiresOwnershipAndSkipsLaterFreeEditsAndDeletedAlarms() {
        trials.enroll(true, true)
        val original = customized(1)
        alarms.save(listOf(original, customized(2), customized(3)))
        repeat(5) { complete(original, first.plusDays(it.toLong())) }
        alarms.update { current -> current.filterNot { it.id == 3L }.map {
            if (it.id == 2L) it.copy(sound = AlarmSound.MORNING_TEMPLE_CALL) else it
        } }
        val converted = alarms.load()
        assertEquals(converted, trials.restore(setOf(1, 2, 3), { false }))
        val restored = trials.restore(setOf(1, 2, 3), { true })
        assertEquals(original.customization, restored.first().customization)
        assertNull(restored.first().trialOriginal)
        assertEquals(converted.last(), restored.last())
        assertEquals(listOf(1L, 2L), restored.map { it.id })
        assertEquals(restored, trials.restore(setOf(1, 2, 3), { true }))
    }

    @Test fun missingFallbackIsPendingRecoveryAndNeverInventsAReplacement() {
        trials.enroll(true, true)
        val alarm = customized(1).copy(trialFallback = null)
        alarms.save(listOf(alarm))
        repeat(5) { complete(alarm, first.plusDays(it.toLong())) }
        assertEquals(alarm, alarms.load().single())
        assertEquals(FreeTransition.PENDING, trials.load().transition)
        assertFalse(trials.load().active)
    }

    @Test fun verificationDefersConversionButNotLocalExpiryAndReconciliationAppliesOnce() {
        trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val last = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(last.token, first.plusDays(4), { false }, { true }))
        assertFalse(trials.load().active)
        assertEquals(FreeTransition.PENDING, trials.load().transition)
        assertEquals(alarm, alarms.load().single())
        assertEquals(FreeTransition.APPLIED, trials.reconcile(false, false).transition)
        val converted = alarms.load().single()
        assertEquals(alarm.customization, converted.trialOriginal)
        assertEquals(alarm.trialFallback, converted.customization)
        assertTrue(trials.complete(last.token, first.plusDays(5), { false }, { false }))
        assertEquals(5, trials.load().dates.size)
        assertEquals(converted, alarms.load().single())
    }

    @Test fun pendingPurchaseResolutionSurvivesRestartUntilOwnershipIsConfirmedFree() {
        trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val last = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(last.token, first.plusDays(4), { false }, { true }))
        val restarted = ProTrialStore(context)
        assertTrue(restarted.load().awaitingPurchaseResolution)
        val unresolved = restarted.reconcile(paid = false, verifying = false, ownershipResolved = false)
        assertEquals(FreeTransition.PENDING, unresolved.transition)
        assertTrue(unresolved.awaitingPurchaseResolution)
        assertFalse(unresolved.active)
        assertEquals(alarm, AlarmStore(context).load().single())
        val later = occurrences.begin(alarm, 1)
        assertTrue(restarted.complete(later.token, first.plusDays(5), { false }, { false }))
        assertTrue(restarted.load().awaitingPurchaseResolution)
        assertEquals(FreeTransition.PENDING, restarted.load().transition)
        assertEquals(alarm, alarms.load().single())
        val resolved = ProTrialStore(context).reconcile(paid = false, verifying = false, ownershipResolved = true)
        assertEquals(FreeTransition.APPLIED, resolved.transition)
        assertFalse(resolved.awaitingPurchaseResolution)
        assertEquals(alarm.trialFallback, alarms.load().single().customization)
    }

    @Test fun recoveredPurchaseRetiresPendingPreviewWithoutConvertingAlarms() {
        trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val last = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(last.token, first.plusDays(4), { false }, { true }))
        val retired = ProTrialStore(context).reconcile(paid = true, verifying = false, recoverablePaid = true, ownershipResolved = true)
        assertTrue(retired.retired)
        assertFalse(retired.awaitingPurchaseResolution)
        assertFalse(retired.active)
        assertFalse(retired.exhausted)
        assertNull(retired.pendingMilestone)
        assertEquals(FreeTransition.NOT_NEEDED, retired.transition)
        assertEquals(alarm, alarms.load().single())
        ProTrialStore(context).reconcile(paid = false, verifying = false, ownershipResolved = true)
        assertEquals(alarm, alarms.load().single())
        assertTrue(trials.load().retired)
    }

    @Test fun failedExpiryCommitKeepsTerminalOutcomeButRollsBackTrialCountAndFutureSettings() {
        trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val previous = trials.load()
        val last = occurrences.begin(alarm, 1)
        var failNextCommit = true
        val failingContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = super.getSharedPreferences(name, mode)
                if (name != "yoga_alarm") return preferences
                return object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val delegate = preferences.edit()
                        return object : SharedPreferences.Editor by delegate {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                delegate.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean {
                                val committed = delegate.commit()
                                if (!failNextCommit) return committed
                                failNextCommit = false
                                return false
                            }
                        }
                    }
                }
            }
        }
        assertFalse(ProTrialStore(failingContext).complete(last.token, first.plusDays(4), { false }, { false }))
        assertFalse(failNextCommit)
        assertEquals(previous, ProTrialStore(context).load())
        assertEquals(alarm, AlarmStore(context).load().single())
        val outcome = AlarmOccurrenceStore(context).find(last.token)!!
        assertTrue(outcome.terminal)
        assertEquals(first.plusDays(4), outcome.completedAt)
        assertNull(occurrences.ringing(last.token))
        assertTrue(trials.complete(last.token, first.plusDays(5), { false }, { false }))
        assertEquals(previous, trials.load())
        assertEquals(alarm, alarms.load().single())
    }

    @Test fun stoppedOrUnknownOccurrencesDoNotConsumeDaysAndPaidCompletionDoesNotChargePreview() {
        trials.enroll(true, true)
        val alarm = AlarmConfig.create(1)
        val stopped = occurrences.begin(alarm, 1)
        occurrences.stop(stopped.token)
        assertFalse(trials.complete(stopped.token, first, { false }, { false }))
        assertFalse(trials.complete("unknown", first, { false }, { false }))
        val paid = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(paid.token, first, { true }, { false }))
        assertTrue(trials.load().dates.isEmpty())
        trials.reconcile(true, false, recoverablePaid = true)
        assertTrue(trials.load().retired)
        assertFalse(trials.load().active)
    }

    private fun customized(id: Long): AlarmConfig {
        val alarm = AlarmConfig.create(id).copy(routine = listOf(PoseStep(YogaPose.CHAIR, 5)), sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        return alarm.copy(trialFallback = alarm.suggestedFreeFallback(), trialCustomization = alarm.customization)
    }

    private fun complete(alarm: AlarmConfig, time: ZonedDateTime) {
        val occurrence = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(occurrence.token, time, { false }, { false }))
    }
}
