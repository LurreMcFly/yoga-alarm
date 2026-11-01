package com.lurremcfly.yogaalarm.data

import android.content.Context
import android.content.SharedPreferences
import com.lurremcfly.yogaalarm.model.FreeTransition
import com.lurremcfly.yogaalarm.model.ProTrial
import com.lurremcfly.yogaalarm.model.TrialEnrollment
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime

class ProTrialStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
    private val trialKey: String get() = if (internalTestTrialEnabled()) INTERNAL_TRIAL_KEY else KEY

    // Call before WakeProgressStore.load() or onboarding writes defaults.
    fun enroll(enabled: Boolean, treatment: Boolean = kotlin.random.Random.nextBoolean()): ProTrial = synchronized(AlarmStorageLock) {
        if (internalTestTrialEnabled()) return@synchronized load()
        if (preferences.contains(KEY)) return@synchronized load()
        val existing = preferences.contains("alarms") ||
            context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).all.isNotEmpty() ||
            context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).all.isNotEmpty()
        val trial = ProTrial(
            enrollment = when {
                existing || !enabled -> TrialEnrollment.INELIGIBLE
                treatment -> TrialEnrollment.PREVIEW
                else -> TrialEnrollment.CONTROL
            },
            enrolledAt = System.currentTimeMillis(),
        )
        val editor = preferences.edit()
        stage(editor, trial)
        if (editor.commit()) trial else ProTrial()
    }

    fun load(): ProTrial = synchronized(AlarmStorageLock) {
        runCatching {
            val json = JSONObject(preferences.getString(trialKey, "{}")!!)
            ProTrial(
                enrollment = TrialEnrollment.valueOf(json.optString("enrollment", "INELIGIBLE")),
                enrolledAt = json.optLong("enrolledAt"),
                dates = json.optString("dates").split(',').filter { it.isNotEmpty() }.map(LocalDate::parse).take(5).toSet(),
                retired = json.optBoolean("retired"),
                pendingMilestone = json.optInt("pending").takeIf { it == 5 },
                presented = json.optString("presented").split(',').mapNotNull(String::toIntOrNull).toSet(),
                transition = FreeTransition.valueOf(json.optString("transition", "NOT_NEEDED")),
                awaitingPurchaseResolution = json.optBoolean("awaitingPurchaseResolution"),
            )
        }.getOrDefault(ProTrial())
    }

    /** Caller runs on IO; all local completion/expiry writes share this checked commit. */
    fun complete(token: String, now: ZonedDateTime, paid: () -> Boolean, verifying: () -> Boolean): Boolean = synchronized(AlarmStorageLock) {
        val occurrences = AlarmOccurrenceStore(context)
        val occurrence = occurrences.find(token) ?: return@synchronized false
        if (occurrence.completedAt != null) return@synchronized true
        if (occurrence.state != com.lurremcfly.yogaalarm.model.OccurrenceState.RINGING) return@synchronized false
        recordCompletion(now.toLocalDate(), hasPaidAccess(paid()), !internalTestingEnabled() && verifying(),
            updatePreview = !internalTestingEnabled() || internalTestTrialEnabled()) { editor ->
            occurrences.stageCompleted(editor, token, now)
        }
    }

    fun internalTestingEnabled(): Boolean = synchronized(AlarmStorageLock) {
        preferences.getBoolean("pro_preview_internal_testing", false)
    }

    fun setInternalTestingEnabled(enabled: Boolean, blocked: () -> Boolean): Boolean = synchronized(AlarmStorageLock) {
        if (blocked() || AlarmOccurrenceStore(context).hasInFlight()) return@synchronized false
        val previous = internalTestingEnabled()
        val committed = preferences.edit().putBoolean("pro_preview_internal_testing", enabled).commit()
        if (!committed) preferences.edit().putBoolean("pro_preview_internal_testing", previous).commit()
        committed
    }

    fun internalTestProEnabled(): Boolean = synchronized(AlarmStorageLock) {
        internalTestingEnabled() && preferences.getBoolean("pro_preview_internal_paid", false)
    }

    fun internalTestTrialEnabled(): Boolean = synchronized(AlarmStorageLock) {
        internalTestingEnabled() && !internalTestProEnabled() && preferences.getBoolean("pro_preview_internal_trial", false)
    }

    fun setInternalTrialDaysRemaining(remaining: Int?, now: LocalDate = LocalDate.now(), blocked: () -> Boolean): Boolean = synchronized(AlarmStorageLock) {
        if (!internalTestingEnabled() || internalTestProEnabled() || remaining != null && remaining !in 1..5 ||
            blocked() || AlarmOccurrenceStore(context).hasInFlight()) return@synchronized false
        val previousEnabled = preferences.getBoolean("pro_preview_internal_trial", false)
        val previousTrial = preferences.getString(INTERNAL_TRIAL_KEY, null)
        val editor = preferences.edit().putBoolean("pro_preview_internal_trial", remaining != null)
        if (remaining != null) stage(editor, ProTrial(
            enrollment = TrialEnrollment.PREVIEW,
            enrolledAt = System.currentTimeMillis(),
            dates = (1..(5 - remaining)).map { now.minusDays(it.toLong()) }.toSet(),
        ), INTERNAL_TRIAL_KEY)
        val committed = editor.commit()
        if (!committed) preferences.edit()
            .putBoolean("pro_preview_internal_trial", previousEnabled)
            .putString(INTERNAL_TRIAL_KEY, previousTrial)
            .commit()
        committed
    }

    fun setInternalTestProEnabled(enabled: Boolean, blocked: () -> Boolean): Boolean = synchronized(AlarmStorageLock) {
        if (!internalTestingEnabled() || blocked() || AlarmOccurrenceStore(context).hasInFlight()) return@synchronized false
        val previous = internalTestProEnabled()
        val previousNonPaying = preferences.getBoolean("pro_preview_internal_non_paying", false)
        val committed = preferences.edit()
            .putBoolean("pro_preview_internal_paid", enabled)
            .remove("pro_preview_internal_non_paying")
            .commit()
        if (!committed) preferences.edit()
            .putBoolean("pro_preview_internal_paid", previous)
            .putBoolean("pro_preview_internal_non_paying", previousNonPaying)
            .commit()
        committed
    }

    // Local preview/editor policy only. Google Play ownership and verification remain authoritative.
    fun hasPaidAccess(paid: Boolean): Boolean = synchronized(AlarmStorageLock) {
        if (internalTestingEnabled()) internalTestProEnabled() else paid
    }

    fun internalExpiryBannerEnabled(): Boolean = synchronized(AlarmStorageLock) {
        internalTestingEnabled() && preferences.getBoolean("pro_preview_internal_expiry_banner", false)
    }

    fun setInternalExpiryBanner(enabled: Boolean, blocked: () -> Boolean): Boolean = synchronized(AlarmStorageLock) {
        if (!internalTestingEnabled() || blocked() || AlarmOccurrenceStore(context).hasInFlight()) return@synchronized false
        val previous = internalExpiryBannerEnabled()
        val committed = preferences.edit().putBoolean("pro_preview_internal_expiry_banner", enabled).commit()
        if (!committed) preferences.edit().putBoolean("pro_preview_internal_expiry_banner", previous).commit()
        committed
    }

    /** Starts a clean preview without restoring alarms or modifying purchases/history. */
    fun resetForInternalTesting(paid: () -> Boolean, verifying: () -> Boolean, blocked: () -> Boolean = { false }): Boolean = synchronized(AlarmStorageLock) {
        if (!internalTestingEnabled() || hasPaidAccess(paid()) || verifying() || blocked() || AlarmOccurrenceStore(context).hasInFlight()) return@synchronized false
        val previous = load()
        val store = AlarmStore(context)
        val current = store.load()
        val editor = preferences.edit()
        stage(editor, ProTrial(enrollment = TrialEnrollment.PREVIEW, enrolledAt = System.currentTimeMillis()))
        // Discard old restoration snapshots without reviving them; keep confirmed future fallbacks.
        store.stage(editor, current.map { it.copy(trialOriginal = null) })
        if (editor.commit()) true else {
            val recovery = preferences.edit()
            stage(recovery, previous)
            store.stage(recovery, current)
            recovery.commit()
            false
        }
    }

    /** Exercises production accounting only; no occurrence or wake-progress record is created. */
    fun advanceDayForInternalTesting(now: ZonedDateTime, paid: () -> Boolean, verifying: () -> Boolean, blocked: () -> Boolean = { false }): Boolean = synchronized(AlarmStorageLock) {
        if (!internalTestingEnabled() || hasPaidAccess(paid()) || verifying() || blocked() || AlarmOccurrenceStore(context).hasInFlight()) return@synchronized false
        val trial = load()
        if (!trial.active) return@synchronized false
        val date = maxOf(now.toLocalDate(), trial.dates.maxOrNull()?.plusDays(1) ?: now.toLocalDate())
        recordCompletion(date, currentPaid = false, currentVerifying = false)
    }

    // Caller holds AlarmStorageLock. Real and synthetic days use the same accounting and expiry commit.
    private fun recordCompletion(
        date: LocalDate,
        currentPaid: Boolean,
        currentVerifying: Boolean,
        updatePreview: Boolean = true,
        stageOutcome: (SharedPreferences.Editor) -> Unit = {},
    ): Boolean {
        val previous = load()
        var trial = if (currentPaid || !updatePreview) previous else previous.complete(date)
        if (updatePreview && trial.exhausted && currentVerifying) trial = trial.copy(awaitingPurchaseResolution = true)
        val store = AlarmStore(context)
        val current = store.load()
        val canConvert = updatePreview && trial.exhausted && !currentPaid && !currentVerifying && !trial.awaitingPurchaseResolution
        val converted = if (canConvert) convert(current) else current
        if (canConvert) {
            trial = trial.copy(transition = if (converted.any { !it.customization.isFree && it.trialOriginal == null }) FreeTransition.PENDING else FreeTransition.APPLIED)
        }
        val editor = preferences.edit()
        stageOutcome(editor)
        stage(editor, trial)
        store.stage(editor, converted)
        val committed = editor.commit()
        if (!committed) {
            // Keep the terminal outcome to prevent re-ringing, but give back this trial use.
            // SharedPreferences changes memory even if disk fails. Do not charge a phantom day.
            val recovery = preferences.edit()
            stage(recovery, previous)
            store.stage(recovery, current)
            recovery.commit()
        }
        return committed
    }

    fun reconcile(paid: Boolean, verifying: Boolean, recoverablePaid: Boolean = false, ownershipResolved: Boolean = true): ProTrial = synchronized(AlarmStorageLock) {
        val previous = load()
        if (internalTestingEnabled() && !internalTestTrialEnabled()) return@synchronized previous
        var trial = previous
        val store = AlarmStore(context)
        val current = store.load()
        var alarms = current
        val paidAccess = hasPaidAccess(paid)
        if (paidAccess && recoverablePaid) trial = trial.retire()
        else if (!paidAccess && (!verifying || internalTestTrialEnabled()) &&
            (!trial.awaitingPurchaseResolution || ownershipResolved || internalTestTrialEnabled()) && trial.exhausted && trial.transition != FreeTransition.APPLIED) {
            alarms = convert(current)
            trial = trial.copy(awaitingPurchaseResolution = false, transition = if (alarms.any { !it.customization.isFree && it.trialOriginal == null }) FreeTransition.PENDING else FreeTransition.APPLIED)
        }
        val editor = preferences.edit()
        stage(editor, trial)
        store.stage(editor, alarms)
        if (editor.commit()) trial else {
            val recovery = preferences.edit()
            stage(recovery, previous)
            store.stage(recovery, current)
            recovery.commit()
            previous
        }
    }

    fun markPresented(milestone: Int) = synchronized(AlarmStorageLock) {
        val previous = load()
        val editor = preferences.edit()
        stage(editor, previous.presented(milestone))
        val committed = editor.commit()
        if (!committed) preferences.edit().also { stage(it, previous) }.commit()
        committed
    }

    fun restore(ids: Set<Long>, paid: () -> Boolean): List<com.lurremcfly.yogaalarm.model.AlarmConfig> = synchronized(AlarmStorageLock) {
        val store = AlarmStore(context)
        if (!hasPaidAccess(paid())) return@synchronized store.load()
        store.update { current -> current.map { alarm ->
            val original = alarm.trialOriginal
            if (alarm.id !in ids || original == null || alarm.customization != alarm.trialFallback) alarm
            else alarm.copy(routine = original.routine, sound = original.sound, trialOriginal = null, trialFallback = null, trialCustomization = null)
        } }
    }

    private fun convert(alarms: List<com.lurremcfly.yogaalarm.model.AlarmConfig>) = alarms.map { alarm ->
        val fallback = alarm.trialFallback
        if (alarm.trialCustomization == null || alarm.trialOriginal != null || fallback?.isFree != true || alarm.customization != alarm.trialCustomization) alarm
        else alarm.copy(routine = fallback.routine, sound = fallback.sound, trialOriginal = alarm.customization)
    }

    private fun stage(editor: SharedPreferences.Editor, trial: ProTrial, key: String = trialKey) {
        editor.putString(key, JSONObject().put("version", 1)
            .put("enrollment", trial.enrollment.name).put("enrolledAt", trial.enrolledAt)
            .put("dates", trial.dates.sorted().joinToString(",")).put("retired", trial.retired)
            .put("pending", trial.pendingMilestone ?: 0).put("presented", trial.presented.sorted().joinToString(","))
            .put("awaitingPurchaseResolution", trial.awaitingPurchaseResolution)
            .put("transition", trial.transition.name).toString())
    }

    private companion object {
        const val KEY = "pro_preview_v1"
        const val INTERNAL_TRIAL_KEY = "pro_preview_internal_trial_v1"
    }
}
