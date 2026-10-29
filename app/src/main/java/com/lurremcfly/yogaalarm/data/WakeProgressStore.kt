package com.lurremcfly.yogaalarm.data

import android.content.Context
import com.lurremcfly.yogaalarm.alarm.AlarmScheduler
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.StreakSchedule
import com.lurremcfly.yogaalarm.model.WakeProgress
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZonedDateTime

class WakeProgressStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE)

    fun load(now: ZonedDateTime = ZonedDateTime.now()): WakeProgress = synchronized(AlarmStorageLock) {
        val today = now.toLocalDate()
        val schedules = if (preferences.contains(KEY_SCHEDULE)) {
            readSchedule()
        } else {
            // Legacy installs have no schedule history. Preserve their saved count and start tracking now.
            createSchedule(AlarmStore(context).load(), emptyList(), now).also(::saveSchedule)
        }
        val checkedAt = preferences.getString(KEY_CHECKED_AT, null)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: now.toLocalDateTime()
        val dueDates = schedules.flatMap { it.datesDueBetween(checkedAt, now.toLocalDateTime()) }.toSet()
        val progress = WakeProgress(
            streakDays = preferences.getInt(KEY_STREAK_DAYS, 0),
            bestStreakDays = preferences.getInt(KEY_BEST_STREAK_DAYS, preferences.getInt(KEY_STREAK_DAYS, 0)),
            lastCompletionDate = preferences.getString(KEY_LAST_COMPLETION_DATE, null)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            emergencyPassMonth = preferences.getString(KEY_EMERGENCY_PASS_MONTH, null)
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: YearMonth.from(today),
            emergencyPassesUsed = preferences.getInt(KEY_EMERGENCY_PASSES_USED, 0),
            completedDates = preferences.getStringSet(KEY_COMPLETED_DATES, emptySet()).orEmpty()
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
            scheduledDates = preferences.getStringSet(KEY_SCHEDULED_DATES, emptySet()).orEmpty()
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() + dueDates,
            upcomingAlarmDates = schedules.flatMap { it.datesBetween(today, today.plusDays(2)) }.toSet(),
        ).current(today)
        save(progress)
        preferences.edit().putString(KEY_CHECKED_AT, now.toLocalDateTime().toString()).apply()
        progress
    }

    fun recordCompletion(now: ZonedDateTime = ZonedDateTime.now()): WakeProgress = synchronized(AlarmStorageLock) {
        val date = now.toLocalDate()
        val last = preferences.getString(KEY_LAST_COMPLETION_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // Recovery can replay a committed occurrence after midnight or after a newer completion.
        // Never move the streak/schedule clock backwards for an already-accounted day.
        val updated = if (last != null && !last.isBefore(date)) {
            val current = load()
            current.copy(completedDates = current.completedDates + date)
        } else load(now).completeRoutine(date)
        check(save(updated)) { "Could not persist completed alarm progress" }
        updated
    }

    fun recordStop(
        consumeEmergencyPass: Boolean,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): WakeProgress = synchronized(AlarmStorageLock) {
        load(now).stopAlarm(now.toLocalDate(), consumeEmergencyPass).also(::save)
    }

    fun recordAlarmDue(now: ZonedDateTime = ZonedDateTime.now()) = synchronized(AlarmStorageLock) {
        val progress = load(now)
        save(progress.copy(scheduledDates = progress.scheduledDates + now.toLocalDate()))
        Unit
    }

    fun updateSchedule(alarms: List<AlarmConfig>, now: ZonedDateTime = ZonedDateTime.now()) = synchronized(AlarmStorageLock) {
        load(now) // Evaluate the previous schedule before an edit can remove an overdue alarm.
        saveSchedule(createSchedule(alarms, readSchedule(), now))
    }

    fun recordScheduledAlarm(alarm: AlarmConfig, occurrence: ZonedDateTime, now: ZonedDateTime) = synchronized(AlarmStorageLock) {
        load(now)
        val schedule = StreakSchedule(alarm.id, occurrence.toLocalDateTime(), alarm.weekdays)
        saveSchedule(readSchedule().filterNot { it.alarmId == alarm.id } + schedule)
    }

    private fun createSchedule(
        alarms: List<AlarmConfig>,
        previous: List<StreakSchedule>,
        now: ZonedDateTime,
    ): List<StreakSchedule> = alarms.filter { it.enabled }.map { alarm ->
        previous.firstOrNull {
            it.alarmId == alarm.id && it.weekdays == alarm.weekdays &&
                it.firstOccurrence.hour == alarm.hour && it.firstOccurrence.minute == alarm.minute
        } ?: StreakSchedule(alarm.id, AlarmScheduler.nextOccurrence(alarm, now).toLocalDateTime(), alarm.weekdays)
    }

    private fun readSchedule(): List<StreakSchedule> = runCatching {
        val array = JSONArray(preferences.getString(KEY_SCHEDULE, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val weekdays = item.getJSONArray("weekdays")
            StreakSchedule(
                item.getLong("id"),
                LocalDateTime.parse(item.getString("first")),
                (0 until weekdays.length()).map { weekdays.getInt(it) }.toSet(),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveSchedule(schedules: List<StreakSchedule>) {
        val array = JSONArray()
        schedules.forEach { schedule ->
            array.put(JSONObject()
                .put("id", schedule.alarmId)
                .put("first", schedule.firstOccurrence.toString())
                .put("weekdays", JSONArray(schedule.weekdays.sorted())))
        }
        preferences.edit().putString(KEY_SCHEDULE, array.toString()).apply()
    }

    private fun save(progress: WakeProgress): Boolean =
        preferences.edit()
            .putInt(KEY_STREAK_DAYS, progress.streakDays)
            .putInt(KEY_BEST_STREAK_DAYS, progress.bestStreakDays)
            .putString(KEY_LAST_COMPLETION_DATE, progress.lastCompletionDate?.toString())
            .putString(KEY_EMERGENCY_PASS_MONTH, progress.emergencyPassMonth.toString())
            .putInt(KEY_EMERGENCY_PASSES_USED, progress.emergencyPassesUsed)
            .putStringSet(KEY_COMPLETED_DATES, progress.completedDates.map { it.toString() }.toSet())
            .putStringSet(KEY_SCHEDULED_DATES, progress.scheduledDates.map { it.toString() }.toSet())
            .commit()

    private companion object {
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_BEST_STREAK_DAYS = "best_streak_days"
        const val KEY_LAST_COMPLETION_DATE = "last_completion_date"
        const val KEY_EMERGENCY_PASS_MONTH = "emergency_pass_month"
        const val KEY_EMERGENCY_PASSES_USED = "emergency_passes_used"
        const val KEY_COMPLETED_DATES = "completed_dates"
        const val KEY_SCHEDULED_DATES = "scheduled_dates"
        const val KEY_SCHEDULE = "streak_schedule"
        const val KEY_CHECKED_AT = "schedule_checked_at"
    }
}
