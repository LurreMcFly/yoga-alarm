package com.lurremcfly.yogaalarm.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.lurremcfly.yogaalarm.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InternalProTrialStoreTest : ProTrialStoreFixture() {
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

    @Test fun internalExpiryAutomaticallyProjectsMissingFallback() {
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.resetForInternalTesting({ false }, { false }))
        val alarm = customized(1).copy(trialFallback = null)
        alarms.save(listOf(alarm))
        repeat(5) { assertTrue(trials.advanceDayForInternalTesting(first, { false }, { false })) }
        assertEquals(alarm.suggestedFreeFallback(), alarms.load().single().customization)
        assertEquals(FreeTransition.APPLIED, trials.load().transition)
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

    @Test fun internalTestSwitchControlsEditingRegardlessOfPlayOwnershipAndPreservesSavedAlarms() {
        val trials = ProTrialStore(context)
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        val premium = AlarmConfig.create(1).copy(sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        assertNull(alarms.saveEdited(premium, null, paid = true))
        assertTrue(alarms.load().isEmpty())
        assertTrue(trials.setInternalTestProEnabled(true, { false }))
        assertEquals(premium, alarms.saveEdited(premium, null, paid = false))
        assertTrue(trials.setInternalTestProEnabled(false, { false }))
        assertEquals(premium, alarms.load().single())
        assertNull(alarms.saveEdited(premium.copy(sound = AlarmSound.RICE_PAPER_DAWN), premium, paid = true))
        assertEquals(premium, alarms.load().single())
        val free = premium.suggestedFreeFallback()
        val freeDraft = premium.copy(routine = free.routine, sound = free.sound)
        assertEquals(freeDraft, alarms.saveEdited(freeDraft, premium, paid = true))
    }

    @Test fun selectedTestTrialAutomaticallyPreparesFallbackEvenForARealProOwner() {
        val trials = ProTrialStore(context)
        assertTrue(trials.setInternalTestingEnabled(true, { false }))
        assertTrue(trials.setInternalTrialDaysRemaining(3, blocked = { false }))
        val premium = AlarmConfig.create(1).copy(sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        val saved = alarms.saveEdited(premium, null, paid = true)!!
        assertEquals(premium.suggestedFreeFallback(), saved.trialFallback)
        val changed = saved.copy(sound = AlarmSound.RICE_PAPER_DAWN)
        val updated = alarms.saveEdited(changed, saved, paid = true)!!
        assertEquals(changed.customization, updated.trialCustomization)
        assertEquals(changed.suggestedFreeFallback(), updated.trialFallback)
    }
}
