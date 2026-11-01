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

    @Test fun expiredLegacyAlarmRequiresFreeReviewToRescheduleAndCanRestoreAfterConfirmation() {
        trials.enroll(true, true)
        val poses = listOf(YogaPose.MOUNTAIN, YogaPose.SQUAT, YogaPose.WARRIOR_TWO, YogaPose.WARRIOR_TWO,
            YogaPose.TRIANGLE, YogaPose.TRIANGLE, YogaPose.GODDESS)
        val legacy = AlarmConfig.create(1).copy(routine = poses.mapIndexed { index, pose -> PoseStep(pose, if (index == 0) 5 else 10) })
        val other = customized(2)
        alarms.save(listOf(legacy, other))
        repeat(5) { complete(other, first.plusDays(it.toLong())) }
        val existing = occurrences.begin(legacy, 2)
        assertTrue(occurrences.snooze(existing.token, 1))
        val changedTime = legacy.copy(minute = (legacy.minute + 1) % 60, enabled = true)
        assertNull(alarms.saveEdited(changedTime, legacy, paid = false, enableOnSave = true))
        assertEquals(legacy, alarms.load().first())
        val disabled = alarms.saveEdited(legacy.copy(enabled = false), legacy, paid = false)!!
        assertNull(alarms.saveEdited(disabled.copy(enabled = true), disabled, paid = false))

        val free = legacy.suggestedFreeFallback().copy(sound = AlarmSound.MORNING_TEMPLE_CALL)
        val freeDraft = changedTime.copy(routine = free.routine, sound = free.sound)
        val confirmed = alarms.saveEdited(freeDraft, disabled, paid = false, enableOnSave = true)!!
        assertTrue(confirmed.customization.isFree)
        assertEquals(free, confirmed.trialFallback)
        assertEquals(legacy.customization, confirmed.trialOriginal)
        assertEquals(confirmed, alarms.saveEdited(freeDraft, disabled, paid = false, enableOnSave = true))
        assertEquals(FreeTransition.APPLIED, trials.reconcile(false, false).transition)
        assertEquals(legacy, occurrences.find(existing.token)?.alarm)
        val next = occurrences.begin(legacy, 2)
        assertEquals(confirmed, next.alarm)
        occurrences.stop(next.token)
        val restored = trials.restore(setOf(legacy.id), { true }).first()
        assertEquals(legacy.customization, restored.customization)
        assertEquals(changedTime.minute, restored.minute)
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

    @Test fun internalControlsAreDisabledWhenTestingModeIsOff() {
        assertFalse(trials.internalTestingEnabled())
        assertFalse(trials.resetForInternalTesting({ false }, { false }))
        assertFalse(trials.advanceDayForInternalTesting(first, { false }, { false }))
        assertEquals(ProTrial(), trials.load())
        context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit().putBoolean("pro_preview_internal_expiry_banner", true).commit()
        assertFalse(trials.internalExpiryBannerEnabled())
        assertFalse(trials.setInternalExpiryBanner(true, { false }))
    }

    @Test fun internalResetForcesPreviewAndPreservesLatestAlarmSettingsAndOtherData() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        assertEquals(TrialEnrollment.INELIGIBLE, trials.enroll(true, true).enrollment)
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        assertEquals(alarm, alarms.load().single())
        repeat(5) { assertTrue(trials.advanceDayForInternalTesting(first, { false }, { false })) }
        val edited = alarms.load().single().copy(name = "Latest", sound = AlarmSound.MORNING_TEMPLE_CALL, enabled = false)
        alarms.save(listOf(edited))
        val prefs = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
        prefs.edit().putString("purchase_test_marker", "preserve").commit()
        val progressBefore = context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).all
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        val reset = trials.load()
        assertTrue(reset.active)
        assertTrue(reset.dates.isEmpty())
        assertTrue(reset.presented.isEmpty())
        assertNull(reset.pendingMilestone)
        assertEquals(FreeTransition.NOT_NEEDED, reset.transition)
        assertEquals(edited.copy(trialOriginal = null), alarms.load().single())
        assertEquals("preserve", prefs.getString("purchase_test_marker", null))
        assertEquals(progressBefore, context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).all)
    }

    @Test fun internalDaysUseProductionMilestonesAndConversionWithoutOccurrencesOrProgress() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        val prefsBefore = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).all
        val progressBefore = context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).all
        repeat(5) { index ->
            assertTrue(trials.advanceDayForInternalTesting(first, { false }, { false }))
            assertEquals(index + 1, trials.load().dates.size)
            assertEquals(if (index == 4) 5 else null, trials.load().pendingMilestone)
        }
        assertEquals((0L..4L).map { first.toLocalDate().plusDays(it) }.toSet(), trials.load().dates)
        assertEquals(FreeTransition.APPLIED, trials.load().transition)
        assertEquals(alarm.trialFallback, alarms.load().single().customization)
        assertEquals(alarm.customization, alarms.load().single().trialOriginal)
        assertFalse(trials.advanceDayForInternalTesting(first, { false }, { false }))
        assertFalse(occurrences.hasInFlight())
        assertTrue(occurrences.pendingCompletions().isEmpty())
        assertEquals(prefsBefore["alarm_occurrences_v1"], context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).all["alarm_occurrences_v1"])
        assertEquals(progressBefore, context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).all)
    }

    @Test fun internalControlsRejectPaidVerificationBlockedAndInFlightStates() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        val previous = trials.load()
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        assertFalse(trials.resetForInternalTesting({ true }, { false }))
        assertFalse(trials.advanceDayForInternalTesting(first, { true }, { false }))
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
        assertFalse(trials.resetForInternalTesting({ false }, { true }))
        assertFalse(trials.advanceDayForInternalTesting(first, { false }, { true }))
        assertFalse(trials.resetForInternalTesting({ false }, { false }, { true }))
        assertFalse(trials.advanceDayForInternalTesting(first, { false }, { false }, { true }))
        val occurrence = occurrences.begin(AlarmConfig.create(1), 1)
        repeat(2) {
            assertFalse(trials.resetForInternalTesting({ false }, { false }))
            assertFalse(trials.advanceDayForInternalTesting(first, { false }, { false }))
            occurrences.snooze(occurrence.token, 0)
        }
        assertEquals(previous, trials.load())
    }

    @Test fun internalResetAllowsReeditedAlarmToConvertOnSecondExpiry() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        val original = customized(1)
        alarms.save(listOf(original))
        repeat(5) { assertTrue(trials.advanceDayForInternalTesting(first, { false }, { false })) }
        assertEquals(original.customization, alarms.load().single().trialOriginal)
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        val base = alarms.load().single()
        assertEquals(original.trialFallback, base.customization)
        assertNull(base.trialOriginal)
        val draft = base.copy(routine = listOf(PoseStep(YogaPose.CHAIR, 10)), sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        val confirmed = draft.copy(trialFallback = draft.suggestedFreeFallback(), trialCustomization = draft.customization)
        assertTrue(trials.setInternalTestingEnabled(false, { false }))
        assertEquals(confirmed, alarms.saveEdited(confirmed, base, paid = false))
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        repeat(5) { assertTrue(trials.advanceDayForInternalTesting(first, { false }, { false })) }
        val converted = alarms.load().single()
        assertEquals(FreeTransition.APPLIED, trials.load().transition)
        assertEquals(confirmed.trialFallback, converted.customization)
        assertEquals(confirmed.customization, converted.trialOriginal)
    }

    @Test fun internalExpiryPreservesMissingFallbackAndReportsPending() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        val alarm = customized(1).copy(trialFallback = null)
        alarms.save(listOf(alarm))
        repeat(5) { assertTrue(trials.advanceDayForInternalTesting(first, { false }, { false })) }
        assertEquals(alarm, alarms.load().single())
        assertEquals(FreeTransition.PENDING, trials.load().transition)
    }

    @Test fun internalBannerPersistsButRejectsBlockedAndInFlightChanges() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertFalse(trials.internalExpiryBannerEnabled())
        assertTrue(trials.setInternalExpiryBanner(true, { false }))
        assertTrue(ProTrialStore(context).internalExpiryBannerEnabled())
        assertFalse(trials.setInternalExpiryBanner(false, { true }))
        val occurrence = occurrences.begin(AlarmConfig.create(1), 1)
        assertFalse(trials.setInternalExpiryBanner(false, { false }))
        occurrences.snooze(occurrence.token, 0)
        assertFalse(trials.setInternalExpiryBanner(false, { false }))
        occurrences.stop(occurrence.token)
        assertTrue(trials.setInternalExpiryBanner(false, { false }))
        assertFalse(trials.internalExpiryBannerEnabled())
    }

    @Test fun internalTestProIsIgnoredWhenTestingModeIsOff() {
        assertFalse(trials.internalTestingEnabled())
        context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit()
            .putBoolean("pro_preview_internal_non_paying", true)
            .putBoolean("pro_preview_internal_paid", true).commit()
        assertFalse(trials.internalTestProEnabled())
        assertTrue(trials.hasPaidAccess(true))
        assertFalse(trials.hasPaidAccess(false))
        assertFalse(trials.setInternalTestProEnabled(false, { false }))
        assertTrue(trials.hasPaidAccess(true))
    }

    @Test fun testingPausesPreviewAccountingAndPaidReconciliationUntilTurnedOff() {
        trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val preview = trials.load()
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertFalse(trials.hasPaidAccess(true))
        assertFalse(trials.hasPaidAccess(false))
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
        trials = ProTrialStore(context)
        assertFalse(trials.internalTestProEnabled())
        val occurrence = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(occurrence.token, first.plusDays(4), { true }, { false }))
        assertTrue(occurrences.find(occurrence.token)!!.terminal)
        assertEquals(preview, trials.load())
        assertEquals(preview, trials.reconcile(true, false, recoverablePaid = true))
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        assertEquals(preview, trials.reconcile(true, false, recoverablePaid = true))
        assertEquals(alarm, alarms.load().single())
        assertTrue(trials.setInternalTestingEnabled(false, { false }))
        assertTrue(trials.hasPaidAccess(true))
        assertFalse(trials.hasPaidAccess(false))
        complete(alarm, first.plusDays(5))
        assertTrue(trials.load().exhausted)
        assertEquals(alarm.trialFallback, alarms.load().single().customization)
    }

    @Test fun testingModePersistsAndCanOnlyChangeWhileIdle() {
        assertFalse(trials.internalTestingEnabled())
        assertTrue(trials.hasPaidAccess(true))
        assertFalse(trials.hasPaidAccess(false))
        assertFalse(trials.setInternalTestingEnabled(true, { true }))
        val occurrence = occurrences.begin(AlarmConfig.create(1), 1)
        assertFalse(trials.setInternalTestingEnabled(true, { false }))
        occurrences.snooze(occurrence.token, 0)
        assertFalse(trials.setInternalTestingEnabled(true, { false }))
        occurrences.stop(occurrence.token)
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(ProTrialStore(context).internalTestingEnabled())
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        val next = occurrences.begin(AlarmConfig.create(1), 1)
        assertFalse(trials.setInternalTestingEnabled(false, { false }))
        occurrences.snooze(next.token, 0)
        assertFalse(trials.setInternalTestingEnabled(false, { false }))
        occurrences.stop(next.token)
        assertTrue(trials.setInternalTestingEnabled(false, { false }))
        assertFalse(ProTrialStore(context).internalTestingEnabled())
        assertFalse(ProTrialStore(context).hasPaidAccess(false))
        assertTrue(ProTrialStore(context).hasPaidAccess(true))
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.internalTestProEnabled())
    }

    @Test fun testingDoesNotApplyPendingPreviewFallbacks() {
        trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val last = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(last.token, first.plusDays(4), { false }, { true }))
        val pending = trials.load()
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertEquals(pending, trials.reconcile(false, false))
        complete(alarm, first.plusDays(5))
        assertEquals(pending, trials.load())
        assertEquals(alarm, alarms.load().single())
        assertTrue(trials.setInternalTestingEnabled(false, { false }))
        assertEquals(FreeTransition.APPLIED, trials.reconcile(false, false).transition)
        assertEquals(alarm.trialFallback, alarms.load().single().customization)
    }

    @Test fun internalTestProToggleRejectsBlockedRingingAndSnoozedStates() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertFalse(trials.setInternalTestProEnabled(true, { true }))
        assertFalse(trials.internalTestProEnabled())
        val occurrence = occurrences.begin(AlarmConfig.create(1), 1)
        assertFalse(trials.setInternalTestProEnabled(true, { false }))
        assertFalse(trials.internalTestProEnabled())
        occurrences.snooze(occurrence.token, 0)
        assertFalse(trials.setInternalTestProEnabled(true, { false }))
        occurrences.stop(occurrence.token)
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        val next = occurrences.begin(AlarmConfig.create(1), 1)
        assertFalse(trials.setInternalTestProEnabled(false, { false }))
        assertTrue(trials.internalTestProEnabled())
        occurrences.stop(next.token)
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
    }

    @Test fun internalTestProPersistsAcrossRestartAndReplacesLegacyOverride() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        val prefs = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pro_preview_internal_non_paying", true).commit()
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        assertFalse(prefs.contains("pro_preview_internal_non_paying"))
        val restarted = ProTrialStore(context)
        assertTrue(restarted.internalTestProEnabled())
        assertTrue(restarted.hasPaidAccess(false))
        assertTrue(restarted.hasPaidAccess(true))
        assertTrue(restarted.setInternalTestProEnabled(false, { false }))
        assertFalse(ProTrialStore(context).internalTestProEnabled())
        assertFalse(ProTrialStore(context).hasPaidAccess(true))
        assertFalse(ProTrialStore(context).hasPaidAccess(false))
    }

    @Test fun failedTestingPreferenceCommitsRestorePreviousModeAndAccess() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        val prefs = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pro_preview_internal_non_paying", true).commit()
        var failNextCommit = true
        val failingContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = super.getSharedPreferences(name, mode)
                if (name != "yoga_alarm") return preferences
                return object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val delegate = preferences.edit()
                        return object : SharedPreferences.Editor by delegate {
                            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                                delegate.putBoolean(key, value)
                                return this
                            }
                            override fun remove(key: String?): SharedPreferences.Editor {
                                delegate.remove(key)
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
        assertFalse(ProTrialStore(failingContext).setInternalTestProEnabled(false, { false }))
        assertFalse(failNextCommit)
        assertTrue(ProTrialStore(context).hasPaidAccess(false))
        assertTrue(prefs.getBoolean("pro_preview_internal_non_paying", false))
        failNextCommit = true
        assertFalse(ProTrialStore(failingContext).setInternalTestingEnabled(false, { false }))
        assertFalse(failNextCommit)
        assertTrue(ProTrialStore(context).internalTestingEnabled())
        assertTrue(ProTrialStore(context).hasPaidAccess(false))
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
        assertTrue(trials.setInternalTrialDaysRemaining(2, first.toLocalDate(), { false }))
        val selected = trials.load()
        failNextCommit = true
        assertFalse(ProTrialStore(failingContext).setInternalTrialDaysRemaining(4, first.toLocalDate(), { false }))
        assertFalse(failNextCommit)
        assertEquals(selected, trials.load())
        failNextCommit = true
        assertFalse(ProTrialStore(failingContext).setInternalTrialDaysRemaining(null, first.toLocalDate(), { false }))
        assertFalse(failNextCommit)
        assertTrue(trials.internalTestTrialEnabled())
        assertEquals(selected, trials.load())
    }

    @Test fun internalTestFreeDoesNotGrantPreviewEditingEvenWithActualPaidOwnership() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
        assertTrue(trials.resetForInternalTesting({ true }, { false }))
        val base = AlarmConfig.create(1)
        alarms.save(listOf(base))
        val premium = customized(1)
        assertNull(alarms.saveEdited(premium.copy(trialFallback = null), base, paid = true))
        assertNull(alarms.saveEdited(premium.copy(trialCustomization = null), base, paid = true))
        assertEquals(base, alarms.load().single())
        assertNull(alarms.saveEdited(premium, base, paid = true))
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        val uncheckedPaidEdit = premium.copy(routine = listOf(PoseStep(YogaPose.CHAIR, 10)), trialFallback = null, trialCustomization = null)
        assertEquals(uncheckedPaidEdit, alarms.saveEdited(uncheckedPaidEdit, base, paid = false))
    }

    @Test fun selectedTrialDayPersistsWithoutChangingNormalPreviewAlarmsOrHistory() {
        val normalPreview = trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        val progress = context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).all
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        for (remaining in 5 downTo 1) {
            assertTrue(trials.setInternalTrialDaysRemaining(remaining, first.toLocalDate(), { false }))
            val restarted = ProTrialStore(context)
            assertTrue(restarted.internalTestTrialEnabled())
            assertTrue(restarted.load().active)
            assertEquals(remaining, restarted.load().remaining)
            assertEquals(5 - remaining, restarted.load().dates.size)
            assertFalse(first.toLocalDate() in restarted.load().dates)
            assertEquals(restarted.load(), restarted.enroll(false))
        }
        val selected = trials.load()
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        assertFalse(trials.internalTestTrialEnabled())
        assertEquals(normalPreview, trials.load())
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
        assertEquals(selected, trials.load())
        assertTrue(trials.setInternalTestingEnabled(false, { false }))
        assertEquals(normalPreview, trials.load())
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertEquals(selected, trials.load())
        assertTrue(trials.setInternalTrialDaysRemaining(null, first.toLocalDate(), { false }))
        assertFalse(trials.internalTestTrialEnabled())
        assertEquals(normalPreview, trials.load())
        assertEquals(alarm, alarms.load().single())
        assertEquals(progress, context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).all)
        assertFalse(occurrences.hasInFlight())
        assertTrue(occurrences.pendingCompletions().isEmpty())
    }

    @Test fun trialDaySelectionRejectsInvalidDisabledPaidBlockedAndInFlightChanges() {
        assertFalse(trials.setInternalTrialDaysRemaining(5, first.toLocalDate(), { false }))
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        assertFalse(trials.setInternalTrialDaysRemaining(5, first.toLocalDate(), { false }))
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
        assertTrue(trials.setInternalTrialDaysRemaining(3, first.toLocalDate(), { false }))
        val selected = trials.load()
        for (remaining in listOf(-1, 0, 6)) {
            assertFalse(trials.setInternalTrialDaysRemaining(remaining, first.toLocalDate(), { false }))
        }
        assertFalse(trials.setInternalTrialDaysRemaining(2, first.toLocalDate(), { true }))
        val occurrence = occurrences.begin(AlarmConfig.create(1), 1)
        repeat(2) {
            assertFalse(trials.setInternalTrialDaysRemaining(2, first.toLocalDate(), { false }))
            assertFalse(trials.setInternalTrialDaysRemaining(null, first.toLocalDate(), { false }))
            occurrences.snooze(occurrence.token, 0)
        }
        assertEquals(selected, trials.load())
        occurrences.stop(occurrence.token)
        assertTrue(trials.setInternalTrialDaysRemaining(2, first.toLocalDate(), { false }))
    }

    @Test fun selectedTrialCountsRealAlarmDatesAndExpiresWithConfirmedFutureFallback() {
        val normalPreview = trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.setInternalTrialDaysRemaining(2, first.toLocalDate(), { false }))
        val firstAlarm = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(firstAlarm.token, first, { true }, { true }))
        assertEquals(1, trials.load().remaining)
        assertTrue(trials.complete(firstAlarm.token, first.plusDays(1), { true }, { true }))
        complete(alarm, first)
        assertEquals(1, trials.load().remaining)
        val lastAlarm = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(lastAlarm.token, first.plusDays(1), { true }, { true }))
        val expired = ProTrialStore(context).load()
        assertTrue(expired.exhausted)
        assertEquals(5, expired.pendingMilestone)
        assertFalse(expired.awaitingPurchaseResolution)
        assertEquals(FreeTransition.APPLIED, expired.transition)
        assertEquals(alarm.trialFallback, alarms.load().single().customization)
        assertEquals(alarm.customization, alarms.load().single().trialOriginal)
        assertEquals(alarm, occurrences.find(lastAlarm.token)!!.alarm)
        assertEquals(expired, trials.reconcile(true, true, recoverablePaid = true))
        assertTrue(trials.markPresented(5))
        assertNull(trials.load().pendingMilestone)
        assertTrue(trials.setInternalTestingEnabled(false, { false }))
        assertEquals(normalPreview, trials.load())
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
