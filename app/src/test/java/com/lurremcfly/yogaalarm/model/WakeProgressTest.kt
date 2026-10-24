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
    fun existingStreakSeedsOnlyRecentMarkersAndRetainsThemForHistory() {
        val existing = WakeProgress(10, today, YearMonth.from(today)).current(today)
        assertEquals((0L..4L).map { today.minusDays(it) }.toSet(), existing.completedDates)
        assertEquals(existing.completedDates, existing.current(today.plusMonths(11)).completedDates)
        assertEquals(emptySet<LocalDate>(), existing.current(today.plusMonths(12)).completedDates)
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
        assertEquals(setOf(today.minusDays(4), today.minusDays(3), today.minusDays(2), today.minusDays(1), today), resumed.completedDates)
        assertEquals(6, resumed.completeRoutine(today.plusMonths(2)).streakDays)
    }

    @Test
    fun overdueScheduledDayIsCheckedBeforeItsHistoryExpires() {
        val progress = WakeProgress(5, today, YearMonth.from(today), scheduledDates = setOf(today.plusDays(1)))
        val later = progress.current(today.plusMonths(13))
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
    fun weekendPauseMarkersAppearOnlyWhenEachDayArrives() {
        val progress = WakeProgress(emergencyPassMonth = YearMonth.from(today)).completeRoutine(today)
        val saturday = today.plusDays(1)
        val sunday = today.plusDays(2)
        assertEquals(emptySet<LocalDate>(), progress.pausedDates(today))
        assertEquals(setOf(saturday), progress.pausedDates(saturday))
        assertEquals(setOf(saturday, sunday), progress.pausedDates(sunday))
    }

    @Test
    fun missedAlarmEndsPauseChainButPreservesEarlierPauses() {
        val completed = today.minusDays(3)
        val progress = WakeProgress(1, completed, YearMonth.from(today),
            completedDates = setOf(completed), scheduledDates = setOf(today.minusDays(1)))
        assertEquals(setOf(today.minusDays(2)), progress.pausedDates(today))
        assertEquals(setOf(today.minusDays(2)),
            progress.completeRoutine(today).pausedDates(today))
    }

    @Test
    fun activeStreakCanPauseBeyondVisibleCompletionHistory() {
        val progress = WakeProgress(5, today.minusDays(10), YearMonth.from(today))
        assertEquals((-9L..0L).map { today.plusDays(it) }.toSet(), progress.pausedDates(today))
        val broken = progress.copy(scheduledDates = setOf(today.minusDays(6))).current(today)
        assertEquals((-9L..-7L).map { today.plusDays(it) }.toSet(), broken.pausedDates(today))
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
        assertEquals(emptySet<LocalDate>(), progress.completeRoutine(today).pausedDates(today))
        assertEquals(setOf(today.plusDays(1)), progress.completeRoutine(today).pausedDates(today.plusDays(1)))
    }

    @Test
    fun completedFridayRemainsVisibleInSevenDayWindowOnTuesday() {
        val progress = WakeProgress(5, today, YearMonth.from(today), completedDates = setOf(today))
        assertEquals(setOf(today), progress.current(today.plusDays(4)).completedDates)
    }

    @Test
    fun historyKeepsOnlyTheTwelveDisplayedCalendarMonths() {
        val first = today.withDayOfMonth(1).minusMonths(11)
        val dates = setOf(first.minusDays(1), first, today.minusMonths(3), today)
        val progress = WakeProgress(emergencyPassMonth = YearMonth.from(today), completedDates = dates, scheduledDates = dates)
            .current(today)
        assertEquals(dates - first.minusDays(1), progress.completedDates)
        assertEquals(dates - first.minusDays(1), progress.scheduledDates)
    }

    @Test
    fun personalBestSurvivesResetAndOnlyIncreasesOnANewRecord() {
        val original = WakeProgress(8, today.minusDays(2), YearMonth.from(today), scheduledDates = setOf(today.minusDays(1)))
        val reset = original.current(today)
        assertEquals(0, reset.streakDays)
        assertEquals(8, reset.bestStreakDays)
        assertEquals(8, reset.completeRoutine(today).bestStreakDays)
        val record = WakeProgress(8, today.minusDays(1), YearMonth.from(today), bestStreakDays = 8).completeRoutine(today)
        assertEquals(9, record.bestStreakDays)
        assertEquals(9, record.completeRoutine(today).bestStreakDays)
        assertEquals(9, record.current(today.plusYears(2)).bestStreakDays)
    }

    @Test
    fun historicalRestDaysSurviveALaterBrokenStreak() {
        val friday = today.minusWeeks(8)
        val monday = friday.plusDays(3)
        val progress = WakeProgress(1, friday, YearMonth.from(today), completedDates = setOf(friday), scheduledDates = setOf(monday))
        val current = progress.current(today)
        assertEquals(0, current.streakDays)
        assertEquals(setOf(friday.plusDays(1), friday.plusDays(2)), current.pausedDates(today))
    }
}
