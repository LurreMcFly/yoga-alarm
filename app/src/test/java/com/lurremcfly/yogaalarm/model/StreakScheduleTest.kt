package com.lurremcfly.yogaalarm.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakScheduleTest {
    private val friday = LocalDate.of(2026, 9, 11)
    private val weekdays = StreakSchedule(1, friday.atTime(7, 0), setOf(1, 2, 3, 4, 5))

    @Test
    fun weekdaysExcludeWeekendAndIncludeMondayAfterRestart() {
        val monday = friday.plusDays(3)
        assertEquals(setOf(friday, monday), weekdays.datesDueBetween(friday.atStartOfDay(), monday.atTime(8, 0)))
        assertEquals(setOf(monday), weekdays.datesDueBetween(friday.atTime(8, 0), monday.atTime(8, 0)))
    }

    @Test
    fun futureAlarmDoesNotBecomeDueUntilItsTime() {
        assertEquals(emptySet<LocalDate>(), weekdays.datesDueBetween(friday.atStartOfDay(), friday.atTime(6, 59)))
        assertEquals(setOf(friday), weekdays.datesDueBetween(friday.atStartOfDay(), friday.atTime(7, 0)))
    }

    @Test
    fun newScheduleDoesNotCreatePastObligations() {
        val monday = friday.plusDays(3)
        val added = weekdays.copy(firstOccurrence = monday.atTime(7, 0))
        assertEquals(setOf(monday), added.datesDueBetween(friday.atStartOfDay(), monday.atTime(8, 0)))
        assertEquals(emptySet<LocalDate>(), added.datesBetween(friday, friday.plusDays(2)))
    }

    @Test
    fun oneOffOnlyCountsItsActualScheduledDate() {
        val once = StreakSchedule(2, friday.plusDays(1).atTime(9, 0), emptySet())
        assertEquals(setOf(friday.plusDays(1)), once.datesDueBetween(friday.atStartOfDay(), friday.plusWeeks(2).atStartOfDay()))
        assertEquals(emptySet<LocalDate>(), once.datesDueBetween(friday.plusDays(2).atStartOfDay(), friday.plusWeeks(2).atStartOfDay()))
    }

    @Test
    fun removingDueAlarmPreservesMissButRemovingBeforeDueDoesNot() {
        val initial = WakeProgress(5, friday.minusDays(1), YearMonth.from(friday))
        val beforeDue = initial.copy(scheduledDates = weekdays.datesDueBetween(friday.atStartOfDay(), friday.atTime(6, 59)))
        val afterDue = initial.copy(scheduledDates = weekdays.datesDueBetween(friday.atStartOfDay(), friday.atTime(8, 0)))
        assertEquals(5, beforeDue.current(friday.plusDays(1)).streakDays)
        assertEquals(0, afterDue.current(friday.plusDays(1)).streakDays)
    }

    @Test
    fun multipleAlarmsOnOneDayOnlyRequireOneCompletion() {
        val later = weekdays.copy(alarmId = 2, firstOccurrence = friday.atTime(9, 0))
        val due = listOf(weekdays, later).flatMap { it.datesDueBetween(friday.atStartOfDay(), friday.atTime(10, 0)) }.toSet()
        assertEquals(setOf(friday), due)
        val progress = WakeProgress(5, friday.minusDays(1), YearMonth.from(friday), scheduledDates = due)
            .completeRoutine(friday)
        assertEquals(6, progress.current(friday.plusDays(1)).streakDays)
    }

    @Test
    fun recurringDatesCrossMonthAndYearBoundaries() {
        val first = LocalDateTime.of(2026, 12, 31, 7, 0)
        val daily = StreakSchedule(1, first, (1..7).toSet())
        assertEquals(setOf(first.toLocalDate(), first.toLocalDate().plusDays(1)),
            daily.datesDueBetween(first, first.plusDays(1)))
    }

    @Test
    fun clockMovingBackwardDoesNotInventDueDates() {
        assertEquals(emptySet<LocalDate>(), weekdays.datesDueBetween(friday.atTime(8, 0), friday.atTime(6, 0)))
    }
}
