package com.lurremcfly.yogaalarm.analytics

import android.content.Context
import android.os.Bundle
import com.lurremcfly.yogaalarm.BuildConfig

/** Coarse funnel events only. Store reporting owns money: never send purchase tokens, order IDs,
 * user content, alarm schedules, poses, camera data, or custom purchase/revenue events here.
 * Firebase's automatic purchase events must not be summed with checkout_outcome as revenue.
 */
class MonetizationAnalytics(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("monetization_measurement", Context.MODE_PRIVATE)
    private val sink by lazy { AnalyticsSink(context.applicationContext) }

    /** Mirror the authoritative local assignment before the first funnel event; never reassign. */
    fun setAssignment(variant: String, experimentVersion: Int, enrolledAtEpochMillis: Long = 0): Boolean {
        if (variant !in setOf("trial", "control", "existing", "ineligible") || experimentVersion < 1) return false
        val stored = preferences.getString("variant", null)
        if (stored != null) return stored == variant && preferences.getInt("experiment_version", 0) == experimentVersion
        return preferences.edit().putString("variant", variant)
            .putInt("experiment_version", experimentVersion)
            .putLong("enrolled_at", enrolledAtEpochMillis.coerceAtLeast(0)).commit()
    }

    /** False means disabled/rejected; true means queued by the SDK, never proof of delivery. */
    fun record(name: String, params: Map<String, String> = emptyMap()): Boolean {
        if (!BuildConfig.MONETIZATION_ANALYTICS_ENABLED || name !in EVENTS ||
            params.any { (key, value) -> !validParameter(key, value) }
        ) return false
        val variant = preferences.getString("variant", null) ?: return false
        if (params["variant"]?.let { it != variant } == true || params["experiment_version"]?.let {
                it != preferences.getInt("experiment_version", 0).toString()
            } == true
        ) return false
        val bundle = Bundle().apply {
            putString("variant", variant)
            putLong("experiment_version", preferences.getInt("experiment_version", 0).toLong())
            putString("platform", "android")
            putString("app_version", BuildConfig.VERSION_NAME)
            // Client ownership/restore does not establish whether a purchase predates enrollment.
            // Keep successful treatment buyers in their assigned cohort until verified reporting
            // can establish historical eligibility consistently for both experiment arms.
            putString("purchase_history", "unknown")
            params.forEach { (key, value) -> putString(key, value) }
        }
        return sink.record(name, bundle)
    }

    /** A local replay guard after SDK acceptance. Disabled/rejected events never consume a key.
     * A process death between SDK acceptance and commit can replay an event; this is not a
     * server delivery receipt or an exactly-once financial ledger. Keys never leave the device.
     */
    fun recordOnce(name: String, dedupeKey: String, params: Map<String, String> = emptyMap()): Boolean =
        synchronized(ONCE_LOCK) {
            if (!BuildConfig.MONETIZATION_ANALYTICS_ENABLED || dedupeKey.length !in 1..80 ||
                dedupeKey.any { !it.isLetterOrDigit() && it !in "_-:." }
            ) return@synchronized false
            val key = "event:$name:$dedupeKey"
            if (preferences.getBoolean(key, false)) return@synchronized false
            if (!record(name, params)) return@synchronized false
            preferences.edit().putBoolean(key, true).commit()
        }

    private fun validParameter(key: String, value: String): Boolean = when (key) {
        "source", "entry_point" -> value in setOf("manual", "locked_feature", "completion_two", "expiry", "setup", "settings", "home")
        "plan" -> value in setOf("monthly", "yearly", "lifetime")
        "feature" -> value in setOf("pose", "duration", "count", "sound", "routine")
        "outcome" -> value in setOf("purchased", "pending", "canceled", "failed", "restored", "unavailable")
        "milestone" -> value in setOf("2", "4", "5")
        "pose_count", "remaining_days" -> value.toIntOrNull()?.let { it in 0..10 } == true
        "hold_customized", "sound_customized" -> value in setOf("true", "false")
        "variant" -> value in setOf("trial", "control", "existing", "ineligible")
        "experiment_version" -> value.toIntOrNull()?.let { it > 0 } == true
        else -> false
    }

    private companion object {
        val ONCE_LOCK = Any()
        val EVENTS = setOf(
            "first_launch", "experiment_assigned", "first_alarm_saved", "first_real_alarm_completed", "alarm_day_completed",
            "trial_milestone_presented", "trial_customization_saved", "paywall_viewed", "offer_unavailable",
            "plan_selected", "checkout_started", "checkout_outcome", "fallback_confirmed",
            "free_transition_applied", "trial_routine_restored", "purchase_restored",
        )
    }
}
