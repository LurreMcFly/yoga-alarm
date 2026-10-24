package com.lurremcfly.yogaalarm.model

import java.time.LocalDate
import java.time.YearMonth

const val MONTHLY_EMERGENCY_PASSES = 3

data class WakeProgress(
    val streakDays: Int = 0,
    val lastCompletionDate: LocalDate? = null,
    val emergencyPassMonth: YearMonth,
    val emergencyPassesUsed: Int = 0,
    val completedDates: Set<LocalDate> = emptySet(),
    val scheduledDates: Set<LocalDate> = emptySet(),
    val upcomingAlarmDates: Set<LocalDate> = emptySet(),
    val bestStreakDays: Int = streakDays,
) {
    val emergencyPassesRemaining: Int
        get() = (MONTHLY_EMERGENCY_PASSES - emergencyPassesUsed).coerceAtLeast(0)

    fun current(today: LocalDate): WakeProgress {
        val currentMonth = YearMonth.from(today)
        val firstDate = today.withDayOfMonth(1).minusMonths(11)
        return copy(
            completedDates = (completedDates.ifEmpty {
                lastCompletionDate?.let { last ->
                    (0 until streakDays.coerceIn(0, 5)).map { last.minusDays(it.toLong()) }.toSet()
                } ?: emptySet()
            }).filterTo(mutableSetOf()) { !it.isBefore(firstDate) },
            bestStreakDays = maxOf(bestStreakDays, streakDays),
            streakDays = if (scheduledDates.any { date ->
                date < today && (lastCompletionDate == null || date > lastCompletionDate)
            }) 0 else streakDays,
            scheduledDates = scheduledDates.filterTo(mutableSetOf()) { it >= firstDate },
            emergencyPassMonth = currentMonth,
            emergencyPassesUsed = if (emergencyPassMonth == currentMonth) emergencyPassesUsed else 0,
        )
    }

    fun pausedDates(today: LocalDate): Set<LocalDate> {
        val progress = current(today)
        val firstDate = today.withDayOfMonth(1).minusMonths(11)
        val alarmDates = progress.scheduledDates + progress.upcomingAlarmDates
        var continuingStreak = progress.streakDays > 0 && progress.lastCompletionDate?.isBefore(firstDate) == true
        val paused = mutableSetOf<LocalDate>()
        var date = firstDate
        while (date <= today) {
            when {
                date <= today && date in progress.completedDates -> continuingStreak = true
                date in alarmDates -> continuingStreak = false
                continuingStreak -> paused += date
            }
            date = date.plusDays(1)
        }
        return paused
    }

    fun completeRoutine(today: LocalDate): WakeProgress {
        val progress = current(today)
        if (progress.lastCompletionDate == today) return progress
        return progress.copy(
            streakDays = progress.streakDays + 1,
            bestStreakDays = maxOf(progress.bestStreakDays, progress.streakDays + 1),
            lastCompletionDate = today,
            completedDates = progress.completedDates + today,
        )
    }

    fun stopAlarm(today: LocalDate, consumeEmergencyPass: Boolean): WakeProgress {
        val progress = current(today)
        return progress.copy(
            scheduledDates = progress.scheduledDates + today,
            emergencyPassesUsed = if (consumeEmergencyPass && progress.emergencyPassesRemaining > 0) {
                progress.emergencyPassesUsed + 1
            } else {
                progress.emergencyPassesUsed
            },
        )
    }
}
