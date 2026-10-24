package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun StreakHistory(
    today: LocalDate,
    completedDates: Set<LocalDate>,
    pausedDates: Set<LocalDate>,
    scheduledDates: Set<LocalDate>,
    weekStartsOnSunday: Boolean,
    months: Int,
    onMonthsChange: (Int) -> Unit,
) {
    val locale = Locale.getDefault()
    val currentMonth = YearMonth.from(today)
    val firstMonth = currentMonth.minusMonths(months - 1L)
    val firstWeekday = if (weekStartsOnSunday) 7 else 1
    val green = Color(0xFF81BD50)
    val yellow = Color(0xFFF5BF42)
    val neutral = Color(0xFFE0E6DF)
    val dayColor: (LocalDate) -> Color = { date ->
        when {
            date > today -> Color(0xFFF3F5F1)
            date in completedDates -> green
            date in pausedDates -> yellow
            date < today && date in scheduledDates -> neutral
            else -> Color(0xFFEDF1EB)
        }
    }
    val description: (LocalDate) -> String = { date ->
        val status = when {
            date > today -> "upcoming"
            date in completedDates -> "yoga completed"
            date in pausedDates -> "rest day, no alarm scheduled"
            date == today -> "not completed yet"
            date in scheduledDates -> "yoga not completed"
            else -> "no recorded history"
        }
        "${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}: $status"
    }
    val range = if (months == 1) currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    else firstMonth.format(DateTimeFormatter.ofPattern(if (firstMonth.year == currentMonth.year) "MMM" else "MMM yyyy", locale)) +
        " – " + currentMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))

    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
        HorizontalDivider(color = Hairline)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(range, modifier = Modifier.weight(1f), color = MutedInk, fontSize = 10.sp)
            for ((label, color) in listOf("Yoga" to green, "Rest" to yellow, "Missed" to neutral)) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(6.dp).background(color, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(3.dp))
                Text(label, color = MutedInk, fontSize = 9.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(130.dp)) {
            when (months) {
                1 -> StreakMonth(currentMonth, firstWeekday, true, dayColor, description)
                3 -> {
                    val start = firstMonth.atDay(1)
                    val weekStart = start.minusDays((start.dayOfWeek.value - firstWeekday + 7L) % 7)
                    val weeks = (ChronoUnit.DAYS.between(weekStart, today) / 7 + 1).toInt()
                    val dates = remember(start, today) { (0L..ChronoUnit.DAYS.between(start, today)).map(start::plusDays) }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().height(16.dp)) {
                            Spacer(Modifier.width(18.dp))
                            repeat(weeks) { week ->
                                val date = weekStart.plusWeeks(week.toLong())
                                val month = YearMonth.from(date)
                                val label = when {
                                    week == 0 -> firstMonth.month.getDisplayName(TextStyle.SHORT, locale)
                                    month != YearMonth.from(date.minusWeeks(1)) && month != firstMonth -> month.month.getDisplayName(TextStyle.SHORT, locale)
                                    else -> ""
                                }
                                Text(label, Modifier.weight(1f), color = MutedInk, fontSize = 9.sp, maxLines = 1)
                            }
                        }
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            Column(Modifier.width(18.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                for (day in 0..6) Text(
                                    if (day % 2 == 0) java.time.DayOfWeek.of((firstWeekday - 1 + day) % 7 + 1).getDisplayName(TextStyle.NARROW, locale) else "",
                                    color = MutedInk, fontSize = 9.sp, lineHeight = 12.sp,
                                )
                            }
                            Canvas(Modifier.weight(1f).fillMaxSize().semantics {
                                role = Role.Image
                                contentDescription = dates.joinToString("; ", transform = description)
                            }) {
                                val gap = 3.dp.toPx()
                                val width = size.width / weeks
                                val height = size.height / 7
                                for (week in 0 until weeks) for (day in 0..6) {
                                    val date = weekStart.plusDays(week * 7L + day)
                                    if (date >= start) drawRoundRect(
                                        dayColor(date), Offset(week * width, day * height),
                                        Size((width - gap).coerceAtLeast(1f), (height - gap).coerceAtLeast(1f)), CornerRadius(3.dp.toPx()),
                                    )
                                }
                            }
                        }
                    }
                }
                12 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(4) { column ->
                                val month = firstMonth.plusMonths(row * 4L + column)
                                Column(Modifier.weight(1f)) {
                                    Text(month.month.getDisplayName(TextStyle.SHORT, locale), color = MutedInk, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
                                    StreakMonth(month, firstWeekday, false, dayColor, description)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = Color(0xFFEDF1EA), shape = RoundedCornerShape(11.dp)) {
            Row(Modifier.fillMaxWidth().padding(3.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for ((value, label) in listOf(1 to "1 month", 3 to "3 months", 12 to "1 year")) {
                    Surface(
                        modifier = Modifier.weight(1f).selectable(months == value, role = Role.Tab, onClick = { onMonthsChange(value) }),
                        shape = RoundedCornerShape(8.dp), color = if (months == value) Color.White else Color.Transparent,
                    ) {
                        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = if (months == value) Forest else MutedInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakMonth(
    month: YearMonth,
    firstWeekday: Int,
    showWeekdays: Boolean,
    dayColor: (LocalDate) -> Color,
    description: (LocalDate) -> String,
) {
    Column(Modifier.fillMaxSize()) {
        if (showWeekdays) Row(Modifier.fillMaxWidth().height(16.dp)) {
            for (day in 0..6) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(java.time.DayOfWeek.of((firstWeekday - 1 + day) % 7 + 1).getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = MutedInk, fontSize = 9.sp)
            }
        }
        val dates = remember(month) { (1..month.lengthOfMonth()).map(month::atDay) }
        Canvas(Modifier.fillMaxSize().semantics {
            role = Role.Image
            contentDescription = dates.joinToString("; ", transform = description)
        }) {
            val gap = if (showWeekdays) 3.dp.toPx() else 1.dp.toPx()
            val width = size.width / 7
            val height = size.height / 6
            val padding = (month.atDay(1).dayOfWeek.value - firstWeekday + 7) % 7
            for (date in dates) {
                val cell = padding + date.dayOfMonth - 1
                drawRoundRect(
                    dayColor(date), Offset(cell % 7 * width, cell / 7 * height),
                    Size((width - gap).coerceAtLeast(1f), (height - gap).coerceAtLeast(1f)), CornerRadius(if (showWeekdays) 3.dp.toPx() else 1.dp.toPx()),
                )
            }
        }
    }
}
