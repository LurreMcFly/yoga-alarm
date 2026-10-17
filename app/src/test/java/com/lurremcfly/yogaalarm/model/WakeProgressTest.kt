package com.lurremcfly.yogaalarm.model

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
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
        val progress = WakeProgress(8, today.minusDays(2), YearMonth.from(today),
            scheduledDates = setOf(today.minusDays(1)))
        assertEquals(0, progress.current(today).streakDays)
        assertEquals(1, progress.completeRoutine(today).streakDays)
    }

    @Test
    fun emergencyStopPreservesStreakForTodayAndConsumesAvailablePass() {
        val progress = WakeProgress(8, today.minusDays(1), YearMonth.from(today), 1)
            .stopAlarm(today, consumeEmergencyPass = true)
        assertEquals(8, progress.streakDays)
        assertEquals(today.minusDays(1), progress.lastCompletionDate)
        assertEquals(0, progress.current(today.plusDays(1)).streakDays)
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
    fun stoppingBeforeCompletionAllowsCompletingLaterToday() {
        for (emergency in listOf(false, true)) {
            val stopped = WakeProgress(8, today.minusDays(1), YearMonth.from(today))
                .stopAlarm(today, consumeEmergencyPass = emergency)
            assertEquals(8, stopped.current(today).streakDays)
            assertEquals(today.minusDays(1), stopped.lastCompletionDate)
            assertEquals(setOf(today), stopped.scheduledDates)
            assertEquals(9, stopped.completeRoutine(today).streakDays)
            assertEquals(9, stopped.completeRoutine(today).current(today.plusDays(1)).streakDays)
            assertEquals(if (emergency) 1 else 0, stopped.emergencyPassesUsed)
        }
    }

    @Test
    fun stoppingWithoutCompletingResetsStreakTheNextDay() {
        for (emergency in listOf(false, true)) {
            val stopped = WakeProgress(8, today.minusDays(1), YearMonth.from(today))
                .stopAlarm(today, consumeEmergencyPass = emergency)
            assertEquals(0, stopped.current(today.plusDays(1)).streakDays)
            assertEquals(1, stopped.completeRoutine(today.plusDays(1)).streakDays)
        }
    }

    @Test
    fun stoppingOrMissingADayPreservesRecentCompletedMarkers() {
        val completed = WakeProgress(emergencyPassMonth = YearMonth.from(today))
            .completeRoutine(today.minusDays(1))
        assertEquals(setOf(today.minusDays(1)), completed.stopAlarm(today, false).completedDates)
        val missed = completed.copy(scheduledDates = setOf(today)).current(today.plusDays(1))
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

    @Test
    fun unscheduledWeekendPausesStreakAndMondayCompletionExtendsIt() {
        val friday = today
        val monday = friday.plusDays(3)
        val progress = WakeProgress(5, friday, YearMonth.from(friday), scheduledDates = setOf(friday))
        assertEquals(5, progress.current(friday.plusDays(1)).streakDays)
        assertEquals(5, progress.current(friday.plusDays(2)).streakDays)
        assertEquals(5, progress.copy(scheduledDates = setOf(friday, monday)).current(monday).streakDays)
        assertEquals(6, progress.completeRoutine(monday).streakDays)
    }

    @Test
    fun missingMondayResetsStreakOnTuesday() {
        val monday = today.plusDays(3)
        val progress = WakeProgress(5, today, YearMonth.from(today), scheduledDates = setOf(monday))
        assertEquals(5, progress.current(monday).streakDays)
        assertEquals(0, progress.current(monday.plusDays(1)).streakDays)
        assertEquals(1, progress.completeRoutine(monday.plusDays(1)).streakDays)
    }

    @Test
    fun disabledAlarmsPauseStreakIndefinitelyWithoutAddingDays() {
        val progress = WakeProgress(5, today, YearMonth.from(today))
        val resumed = progress.current(today.plusMonths(2))
        assertEquals(5, resumed.streakDays)
        assertEquals(today, resumed.lastCompletionDate)
        assertEquals(emptySet<LocalDate>(), resumed.completedDates)
        assertEquals(6, resumed.completeRoutine(today.plusMonths(2)).streakDays)
    }

    @Test
    fun overdueScheduledDayIsCheckedBeforeItsHistoryExpires() {
        val progress = WakeProgress(5, today, YearMonth.from(today), scheduledDates = setOf(today.plusDays(1)))
        val later = progress.current(today.plusMonths(1))
        assertEquals(0, later.streakDays)
        assertEquals(emptySet<LocalDate>(), later.scheduledDates)
    }

    @Test
    fun missedDateBeforeLastCompletionDoesNotEraseNewStreak() {
        val progress = WakeProgress(1, today, YearMonth.from(today), scheduledDates = setOf(today.minusDays(1)))
        assertEquals(1, progress.current(today.plusDays(3)).streakDays)
    }

    @Test
    fun freshInstallHasNoPausedDates() {
        val progress = WakeProgress(emergencyPassMonth = YearMonth.from(today))
        assertEquals(emptySet<LocalDate>(), progress.pausedDates(today))
    }

    @Test
    fun pausedDatesOnlyFollowACompletion() {
        val progress = WakeProgress(emergencyPassMonth = YearMonth.from(today)).completeRoutine(today)
        assertEquals(setOf(today.plusDays(1), today.plusDays(2)), progress.pausedDates(today))
    }

    @Test
    fun missedAlarmEndsPauseChainButPreservesEarlierPauses() {
        val completed = today.minusDays(3)
        val progress = WakeProgress(1, completed, YearMonth.from(today),
            completedDates = setOf(completed), scheduledDates = setOf(today.minusDays(1)))
        assertEquals(setOf(today.minusDays(2)), progress.pausedDates(today))
        assertEquals(setOf(today.minusDays(2), today.plusDays(1), today.plusDays(2)),
            progress.completeRoutine(today).pausedDates(today))
    }

    @Test
    fun activeStreakCanPauseBeyondVisibleCompletionHistory() {
        val progress = WakeProgress(5, today.minusDays(10), YearMonth.from(today))
        assertEquals((-4L..2L).map { today.plusDays(it) }.toSet(), progress.pausedDates(today))
        val broken = progress.copy(scheduledDates = setOf(today.minusDays(6))).current(today)
        assertEquals(emptySet<LocalDate>(), broken.pausedDates(today))
    }

    @Test
    fun upcomingAlarmMustBeCompletedBeforeFollowingDatesCanPause() {
        val progress = WakeProgress(emergencyPassMonth = YearMonth.from(today))
            .completeRoutine(today)
            .copy(upcomingAlarmDates = setOf(today.plusDays(1)))
        assertEquals(emptySet<LocalDate>(), progress.pausedDates(today))
    }

    @Test
    fun stoppedAlarmDoesNotShowAsPausedOrEnableFuturePauses() {
        val progress = WakeProgress(emergencyPassMonth = YearMonth.from(today))
            .completeRoutine(today.minusDays(1))
            .stopAlarm(today, consumeEmergencyPass = false)
        assertEquals(1, progress.streakDays)
        assertEquals(emptySet<LocalDate>(), progress.pausedDates(today))
        assertEquals(setOf(today.plusDays(1), today.plusDays(2)), progress.completeRoutine(today).pausedDates(today))
    }

    @Test
    fun completedFridayRemainsVisibleInSevenDayWindowOnTuesday() {
        val progress = WakeProgress(5, today, YearMonth.from(today), completedDates = setOf(today))
        assertEquals(setOf(today), progress.current(today.plusDays(4)).completedDates)
    }
}
