package com.lurremcfly.yogaalarm.alarm

import com.lurremcfly.yogaalarm.model.AlarmConfig
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSchedulerTest {
    private val stockholm = ZoneId.of("Europe/Stockholm")

    @Test
    fun oneTimeAlarmUsesTodayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 8, 28, 6, 30, 0, 0, stockholm)
        val result = AlarmScheduler.nextOccurrence(alarm(hour = 7, minute = 0), now)

        assertEquals(now.toLocalDate(), result.toLocalDate())
        assertEquals(7, result.hour)
        assertEquals(0, result.minute)
    }

    @Test
    fun oneTimeAlarmMovesToTomorrowAfterTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 8, 28, 7, 1, 0, 0, stockholm)
        val result = AlarmScheduler.nextOccurrence(alarm(hour = 7, minute = 0), now)

        assertEquals(now.toLocalDate().plusDays(1), result.toLocalDate())
    }

    @Test
    fun repeatingAlarmFindsNextSelectedWeekday() {
        val now = ZonedDateTime.of(2026, 8, 28, 8, 0, 0, 0, stockholm)
        val result = AlarmScheduler.nextOccurrence(
            alarm(hour = 7, minute = 0, weekdays = setOf(DayOfWeek.MONDAY.value)),
            now,
        )

        assertEquals(DayOfWeek.MONDAY, result.dayOfWeek)
        assertEquals(7, result.hour)
    }

    @Test
    fun repeatingAlarmDoesNotRescheduleIntoThePastOnSameDay() {
        val now = ZonedDateTime.of(2026, 8, 31, 7, 1, 0, 0, stockholm)
        val result = AlarmScheduler.nextOccurrence(
            alarm(hour = 7, minute = 0, weekdays = setOf(DayOfWeek.MONDAY.value)),
            now,
        )

        assertEquals(now.toLocalDate().plusWeeks(1), result.toLocalDate())
    }

    @Test
    fun daylightSavingGapMovesToFirstValidLocalTime() {
        val now = ZonedDateTime.of(2026, 3, 29, 1, 30, 0, 0, stockholm)
        val result = AlarmScheduler.nextOccurrence(alarm(hour = 2, minute = 30), now)

        assertEquals(now.toLocalDate(), result.toLocalDate())
        assertEquals(3, result.hour)
        assertEquals(30, result.minute)
        assertEquals(stockholm, result.zone)
    }

    private fun alarm(hour: Int, minute: Int, weekdays: Set<Int> = emptySet()) =
        AlarmConfig.create(id = 1L).copy(hour = hour, minute = minute, weekdays = weekdays)
}
