package com.lurremcfly.yogaalarm.model

import java.time.LocalDate
import java.time.LocalDateTime

/** The first scheduled occurrence is retained even when a one-off alarm disables itself. */
data class StreakSchedule(
    val alarmId: Long,
    val firstOccurrence: LocalDateTime,
    val weekdays: Set<Int>,
) {
    fun datesBetween(start: LocalDate, end: LocalDate): Set<LocalDate> {
        if (end < start) return emptySet()
        if (weekdays.isEmpty()) {
            val date = firstOccurrence.toLocalDate()
            return if (date in start..end) setOf(date) else emptySet()
        }
        val dates = mutableSetOf<LocalDate>()
        var date = maxOf(start, firstOccurrence.toLocalDate())
        while (date <= end) {
            if (date.dayOfWeek.value in weekdays) dates += date
            date = date.plusDays(1)
        }
        return dates
    }

    fun datesDueBetween(from: LocalDateTime, until: LocalDateTime): Set<LocalDate> =
        datesBetween(from.toLocalDate(), until.toLocalDate()).filterTo(mutableSetOf()) { date ->
            val occurrence = date.atTime(firstOccurrence.toLocalTime())
            occurrence >= from && occurrence <= until
        }
}
