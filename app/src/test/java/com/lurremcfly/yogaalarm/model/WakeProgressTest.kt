package com.lurremcfly.yogaalarm.model

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeProgressTest {
    private val today = LocalDate.of(2026, 9, 11)

    @Test
    fun completingOnConsecutiveDaysBuildsStreak() {
        val progress = WakeProgress(4, today.minusDays(1), YearMonth.from(today))
        assertEquals(5, progress.completeRoutine(today).streakDays)
    }

    @Test
    fun multipleCompletionsOnSameDayCountOnce() {
        val progress = WakeProgress(4, today, YearMonth.from(today))
        assertEquals(progress, progress.completeRoutine(today))
    }

    @Test
    fun missedDayResetsStreakBeforeNextCompletion() {
        val progress = WakeProgress(8, today.minusDays(2), YearMonth.from(today))
        assertEquals(0, progress.current(today).streakDays)
        assertEquals(1, progress.completeRoutine(today).streakDays)
    }

    @Test
    fun emergencyStopBreaksStreakAndConsumesAvailablePass() {
        val progress = WakeProgress(8, today.minusDays(1), YearMonth.from(today), 1)
            .stopAlarm(today, consumeEmergencyPass = true)
        assertEquals(0, progress.streakDays)
        assertNull(progress.lastCompletionDate)
        assertEquals(2, progress.emergencyPassesUsed)
        assertEquals(1, progress.emergencyPassesRemaining)
    }

    @Test
    fun emergencyPassesResetInNewMonth() {
        val progress = WakeProgress(emergencyPassMonth = YearMonth.of(2026, 8), emergencyPassesUsed = 3)
            .current(today)
        assertEquals(0, progress.emergencyPassesUsed)
        assertEquals(3, progress.emergencyPassesRemaining)
    }

    @Test
    fun emergencyPassUsageNeverExceedsMonthlyLimit() {
        val progress = WakeProgress(emergencyPassMonth = YearMonth.from(today), emergencyPassesUsed = 3)
            .stopAlarm(today, consumeEmergencyPass = true)
        assertEquals(3, progress.emergencyPassesUsed)
        assertEquals(0, progress.emergencyPassesRemaining)
    }
}
