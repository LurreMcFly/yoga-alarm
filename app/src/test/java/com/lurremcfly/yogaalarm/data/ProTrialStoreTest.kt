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

abstract class ProTrialStoreFixture {
    protected lateinit var context: Context
    protected lateinit var trials: ProTrialStore
    protected lateinit var alarms: AlarmStore
    protected lateinit var occurrences: AlarmOccurrenceStore
    protected val first = ZonedDateTime.parse("2026-10-01T23:59:59+02:00[Europe/Stockholm]")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        listOf("yoga_alarm", "onboarding", "wake_progress").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        trials = ProTrialStore(context)
        alarms = AlarmStore(context)
        occurrences = AlarmOccurrenceStore(context)
    }

    protected fun customized(id: Long): AlarmConfig {
        val alarm = AlarmConfig.create(id).copy(routine = listOf(PoseStep(YogaPose.CHAIR, 5)), sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        return alarm.copy(trialFallback = alarm.suggestedFreeFallback(), trialCustomization = alarm.customization)
    }

    protected fun complete(alarm: AlarmConfig, time: ZonedDateTime) {
        val occurrence = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(occurrence.token, time, { false }, { false }))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProTrialStoreTest : ProTrialStoreFixture() {
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

    @Test fun purchaseKeepsConvertedCurrentRoutine() {
        trials.enroll(true, true)
        val original = customized(1)
        alarms.save(listOf(original))
        repeat(5) { complete(original, first.plusDays(it.toLong())) }
        val converted = alarms.load()
        trials.reconcile(true, false, recoverablePaid = true)
        assertEquals(converted, alarms.load())
        assertTrue(trials.load().retired)
    }

    @Test fun missingFallbackConvertsAutomatically() {
        trials.enroll(true, true)
        val alarm = customized(1).copy(trialFallback = null, trialCustomization = null)
        alarms.save(listOf(alarm))
        repeat(5) { complete(alarm, first.plusDays(it.toLong())) }
        assertEquals(alarm.suggestedFreeFallback(), alarms.load().single().customization)
        assertEquals(FreeTransition.APPLIED, trials.load().transition)
    }

    @Test fun legacyAppliedMarkerAndArchivedOriginalDoNotPreventCurrentProjection() {
        trials.enroll(true, true)
        val old = customized(1)
        alarms.save(listOf(old))
        repeat(5) { complete(old, first.plusDays(it.toLong())) }
        val latest = old.copy(routine = listOf(PoseStep(YogaPose.CHAIR, 10), PoseStep(YogaPose.TREE, 45), PoseStep(YogaPose.TRIANGLE, 10)),
            trialOriginal = old.customization)
        alarms.save(listOf(latest))
        assertEquals(latest, alarms.load().single()) // load itself never migrates.
        val unresolved = trials.reconcile(paid = false, verifying = false, ownershipResolved = false)
        assertTrue(unresolved.awaitingPurchaseResolution)
        assertEquals(latest, alarms.load().single())
        trials.reconcile(false, false)
        assertEquals(latest.suggestedFreeFallback(), alarms.load().single().customization)
        val converted = alarms.load()
        trials.reconcile(false, false)
        assertEquals(converted, alarms.load())
    }

    @Test fun legacyTimeSaveDoesNotBypassPurchaseVerificationOrRequireReview() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        trials.enroll(true, true)
        val alarm = customized(1).copy(trialFallback = null, trialCustomization = null)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val last = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(last.token, first.plusDays(4), { false }, { true }))
        val edited = alarms.saveEdited(alarm.copy(hour = (alarm.hour + 1) % 24), alarm,
            paid = false, enableOnSave = true)!!
        assertEquals(alarm.customization, edited.customization)
        assertTrue(edited.enabled)
        trials.reconcile(false, true)
        assertEquals(edited, alarms.load().single())
        trials.reconcile(false, false)
        assertEquals(edited.suggestedFreeFallback(), alarms.load().single().customization)
        assertEquals(edited.hour, alarms.load().single().hour)
        assertEquals(alarm, occurrences.find(last.token)!!.alarm)
    }

    @Test fun pendingPaymentCountsDateOnceWithoutGrantingOwnershipAndDefersConversionAcrossRestart() {
        trials.enroll(true, true)
        val alarm = customized(1)
        alarms.save(listOf(alarm))
        repeat(4) { complete(alarm, first.plusDays(it.toLong())) }
        val pending = occurrences.begin(alarm, 1)
        assertTrue(trials.complete(pending.token, first.plusDays(4), { false }, { true }))
        assertEquals(0, trials.load().remaining)
        assertTrue(trials.load().awaitingPurchaseResolution)
        assertFalse(trials.hasPaidAccess(false))
        assertNotNull(occurrences.find(pending.token)!!.completedAt)
        assertTrue(trials.complete(pending.token, first.plusDays(4), { false }, { true }))
        assertEquals(5, trials.load().dates.size)
        val restarted = ProTrialStore(context)
        restarted.reconcile(false, false, ownershipResolved = false)
        assertTrue(restarted.load().awaitingPurchaseResolution)
        assertEquals(alarm, alarms.load().single())
        restarted.reconcile(false, false, ownershipResolved = true)
        assertFalse(restarted.load().awaitingPurchaseResolution)
        assertEquals(0, restarted.load().remaining)
        assertEquals(alarm.suggestedFreeFallback(), alarms.load().single().customization)
        assertTrue(restarted.complete(pending.token, first.plusDays(4), { false }, { false }))
        assertEquals(5, restarted.load().dates.size)
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
}
