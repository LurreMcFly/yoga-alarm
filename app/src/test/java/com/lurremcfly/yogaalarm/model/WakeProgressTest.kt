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
        val completed = progress.completeRoutine(today)
        assertEquals(4, completed.streakDays)
        assertEquals(today, completed.lastCompletionDate)
        assertEquals(completed, completed.completeRoutine(today))
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
    fun stoppingAfterCompletionPreservesTodayAndTomorrowsStreak() {
        val completed = WakeProgress(8, today.minusDays(1), YearMonth.from(today))
            .completeRoutine(today)
        for (emergency in listOf(false, true)) {
            val stopped = completed.stopAlarm(today, consumeEmergencyPass = emergency)
            assertEquals(9, stopped.streakDays)
            assertEquals(today, stopped.lastCompletionDate)
            assertEquals(9, stopped.completeRoutine(today).streakDays)
            assertEquals(10, stopped.completeRoutine(today.plusDays(1)).streakDays)
            assertEquals(if (emergency) 1 else 0, stopped.emergencyPassesUsed)
        }
    }

    @Test
    fun normalStopBeforeTodayCompletionBreaksStreak() {
        val stopped = WakeProgress(8, today.minusDays(1), YearMonth.from(today))
            .stopAlarm(today, consumeEmergencyPass = false)
        assertEquals(0, stopped.streakDays)
        assertNull(stopped.lastCompletionDate)
        assertEquals(1, stopped.completeRoutine(today).streakDays)
    }

    @Test
    fun stoppingOrMissingADayPreservesRecentCompletedMarkers() {
        val completed = WakeProgress(emergencyPassMonth = YearMonth.from(today))
            .completeRoutine(today.minusDays(1))
        assertEquals(setOf(today.minusDays(1)), completed.stopAlarm(today, false).completedDates)
        val missed = completed.current(today.plusDays(1))
        assertEquals(0, missed.streakDays)
        assertEquals(setOf(today.minusDays(1)), missed.completedDates)
        assertEquals(setOf(today.minusDays(1), today.plusDays(1)), missed.completeRoutine(today.plusDays(1)).completedDates)
    }

    @Test
    fun existingStreakSeedsRecentMarkersAndOldMarkersExpire() {
        val existing = WakeProgress(10, today, YearMonth.from(today)).current(today)
        assertEquals((0L..4L).map { today.minusDays(it) }.toSet(), existing.completedDates)
        assertEquals(setOf(today), existing.current(today.plusDays(4)).completedDates)
        assertEquals(emptySet<LocalDate>(), existing.current(today.plusDays(5)).completedDates)
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
