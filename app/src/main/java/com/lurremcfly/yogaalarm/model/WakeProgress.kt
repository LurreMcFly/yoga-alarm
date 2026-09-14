package com.lurremcfly.yogaalarm.model

import java.time.LocalDate
import java.time.YearMonth

const val MONTHLY_EMERGENCY_PASSES = 3

data class WakeProgress(
    val streakDays: Int = 0,
    val lastCompletionDate: LocalDate? = null,
    val emergencyPassMonth: YearMonth,
    val emergencyPassesUsed: Int = 0,
) {
    val emergencyPassesRemaining: Int
        get() = (MONTHLY_EMERGENCY_PASSES - emergencyPassesUsed).coerceAtLeast(0)

    fun current(today: LocalDate): WakeProgress {
        val currentMonth = YearMonth.from(today)
        return copy(
            streakDays = if (lastCompletionDate == today || lastCompletionDate == today.minusDays(1)) streakDays else 0,
            emergencyPassMonth = currentMonth,
            emergencyPassesUsed = if (emergencyPassMonth == currentMonth) emergencyPassesUsed else 0,
        )
    }

    fun completeRoutine(today: LocalDate): WakeProgress {
        val progress = current(today)
        if (progress.lastCompletionDate == today) return progress
        return progress.copy(
            streakDays = if (progress.lastCompletionDate == today.minusDays(1)) progress.streakDays + 1 else 1,
            lastCompletionDate = today,
        )
    }

    fun stopAlarm(today: LocalDate, consumeEmergencyPass: Boolean): WakeProgress {
        val progress = current(today)
        return progress.copy(
            streakDays = 0,
            lastCompletionDate = null,
            emergencyPassesUsed = if (consumeEmergencyPass && progress.emergencyPassesRemaining > 0) {
                progress.emergencyPassesUsed + 1
            } else {
                progress.emergencyPassesUsed
            },
        )
    }
}
