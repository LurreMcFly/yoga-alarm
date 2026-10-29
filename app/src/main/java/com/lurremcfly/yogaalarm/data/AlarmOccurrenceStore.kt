package com.lurremcfly.yogaalarm.data

import android.content.Context
import android.content.SharedPreferences
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmOccurrence
import com.lurremcfly.yogaalarm.model.OccurrenceState
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.util.UUID

class AlarmOccurrenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
    private val alarms = AlarmStore(context)

    fun find(token: String?): AlarmOccurrence? = synchronized(AlarmStorageLock) {
        records().firstOrNull { it.token == token }
    }

    fun pendingCompletions(): List<AlarmOccurrence> = synchronized(AlarmStorageLock) {
        records().filter { it.state == OccurrenceState.COMPLETED && !it.progressRecorded && it.completedAt != null }
    }

    fun markProgressRecorded(token: String): Boolean = synchronized(AlarmStorageLock) {
        val previous = records()
        val committed = write(preferences.edit(), previous.map {
            if (it.token == token && it.state == OccurrenceState.COMPLETED) it.copy(progressRecorded = true) else it
        }).commit()
        if (!committed) write(preferences.edit(), previous).commit()
        committed
    }

    fun activeForAlarm(alarmId: Long): AlarmOccurrence? = synchronized(AlarmStorageLock) {
        records().lastOrNull { it.alarm.id == alarmId && !it.terminal }
    }

    fun hasInFlight(): Boolean = synchronized(AlarmStorageLock) { records().any { !it.terminal } }

    fun begin(alarm: AlarmConfig, remainingSnoozes: Int): AlarmOccurrence = synchronized(AlarmStorageLock) {
        val current = alarms.load().firstOrNull { it.id == alarm.id } ?: alarm
        val occurrence = AlarmOccurrence(UUID.randomUUID().toString(), current, remainingSnoozes)
        write(preferences.edit(), records().map {
            if (it.alarm.id == alarm.id && !it.terminal) it.copy(state = OccurrenceState.STOPPED) else it
        } + occurrence).commit()
        occurrence
    }

    fun snooze(token: String, remainingSnoozes: Int): Boolean = synchronized(AlarmStorageLock) {
        val record = find(token)?.takeUnless { it.terminal } ?: return@synchronized false
        val previous = records()
        val committed = write(preferences.edit(), previous.map {
            if (it.token == token) record.copy(state = OccurrenceState.SNOOZED, remainingSnoozes = remainingSnoozes) else it
        }).commit()
        if (!committed) write(preferences.edit(), previous).commit()
        committed
    }

    fun ringing(token: String): AlarmOccurrence? = synchronized(AlarmStorageLock) {
        val record = find(token)?.takeUnless { it.terminal } ?: return@synchronized null
        val result = record.copy(state = OccurrenceState.RINGING)
        write(preferences.edit(), records().map { if (it.token == token) result else it }).commit()
        result
    }

    fun stop(token: String, onlyIfRinging: Boolean = false): Boolean = synchronized(AlarmStorageLock) {
        write(preferences.edit(), records().map {
            if (it.token == token && !it.terminal && (!onlyIfRinging || it.state == OccurrenceState.RINGING)) {
                it.copy(state = OccurrenceState.STOPPED)
            } else it
        }).commit()
    }

    fun cancelAlarm(alarmId: Long) = synchronized(AlarmStorageLock) {
        write(preferences.edit(), records().map {
            if (it.alarm.id == alarmId && !it.terminal) it.copy(state = OccurrenceState.STOPPED) else it
        }).commit()
    }

    fun clearSnoozes() = synchronized(AlarmStorageLock) {
        write(preferences.edit(), records().map {
            if (it.state == OccurrenceState.SNOOZED) it.copy(state = OccurrenceState.STOPPED) else it
        }).commit()
    }

    // Caller holds AlarmStorageLock and commits this editor with trial/future alarm changes.
    internal fun stageCompleted(editor: SharedPreferences.Editor, token: String, now: ZonedDateTime) {
        write(editor, records().map {
            if (it.token == token && it.state == OccurrenceState.RINGING) {
                it.copy(state = OccurrenceState.COMPLETED, completedAt = now)
            } else it
        })
    }

    private fun records(): List<AlarmOccurrence> = runCatching {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching decode@{
            val value = array.getJSONObject(index)
            AlarmOccurrence(
                token = value.getString("token"),
                alarm = alarms.decode(value.getString("alarm")) ?: return@decode null,
                remainingSnoozes = value.getInt("remaining"),
                state = OccurrenceState.valueOf(value.getString("state")),
                completedAt = value.optString("completedAt").takeIf { it.isNotEmpty() }?.let(ZonedDateTime::parse),
                progressRecorded = value.optBoolean("progressRecorded", false),
            )
        }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun write(editor: SharedPreferences.Editor, records: List<AlarmOccurrence>): SharedPreferences.Editor {
        val bounded = records.filterNot { it.terminal } + records.filter { it.terminal }.takeLast(32)
        return editor.putString(KEY, JSONArray().apply {
            bounded.forEach { record -> put(JSONObject().apply {
                put("token", record.token)
                put("alarm", alarms.encode(record.alarm))
                put("remaining", record.remainingSnoozes)
                put("state", record.state.name)
                put("completedAt", record.completedAt?.toString().orEmpty())
                put("progressRecorded", record.progressRecorded)
            }) }
        }.toString())
    }

    private companion object { const val KEY = "alarm_occurrences_v1" }
}
