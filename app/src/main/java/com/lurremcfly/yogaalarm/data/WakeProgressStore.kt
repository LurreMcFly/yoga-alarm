package com.lurremcfly.yogaalarm.data

import android.content.Context
import com.lurremcfly.yogaalarm.model.WakeProgress
import java.time.LocalDate
import java.time.YearMonth

class WakeProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE)

    fun load(today: LocalDate = LocalDate.now()): WakeProgress {
        val progress = WakeProgress(
            streakDays = preferences.getInt(KEY_STREAK_DAYS, 0),
            lastCompletionDate = preferences.getString(KEY_LAST_COMPLETION_DATE, null)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            emergencyPassMonth = preferences.getString(KEY_EMERGENCY_PASS_MONTH, null)
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: YearMonth.from(today),
            emergencyPassesUsed = preferences.getInt(KEY_EMERGENCY_PASSES_USED, 0),
        ).current(today)
        save(progress)
        return progress
    }

    fun recordCompletion(today: LocalDate = LocalDate.now()): WakeProgress =
        load(today).completeRoutine(today).also(::save)

    fun recordStop(
        consumeEmergencyPass: Boolean,
        today: LocalDate = LocalDate.now(),
    ): WakeProgress = load(today).stopAlarm(today, consumeEmergencyPass).also(::save)

    private fun save(progress: WakeProgress) {
        preferences.edit()
            .putInt(KEY_STREAK_DAYS, progress.streakDays)
            .putString(KEY_LAST_COMPLETION_DATE, progress.lastCompletionDate?.toString())
            .putString(KEY_EMERGENCY_PASS_MONTH, progress.emergencyPassMonth.toString())
            .putInt(KEY_EMERGENCY_PASSES_USED, progress.emergencyPassesUsed)
            .apply()
    }

    private companion object {
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_LAST_COMPLETION_DATE = "last_completion_date"
        const val KEY_EMERGENCY_PASS_MONTH = "emergency_pass_month"
        const val KEY_EMERGENCY_PASSES_USED = "emergency_passes_used"
    }
}
