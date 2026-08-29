package com.yogaalarm.prototype.data

import android.content.Context
import android.net.Uri
import com.yogaalarm.prototype.model.AlarmConfig
import com.yogaalarm.prototype.model.AlarmSound
import com.yogaalarm.prototype.model.PoseStep
import com.yogaalarm.prototype.model.YogaPose

class AlarmStore(context: Context) {
    private val preferences = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)

    fun load(): List<AlarmConfig> = preferences.getString(KEY_ALARMS, null)
        ?.lineSequence()
        ?.mapNotNull(::decode)
        ?.toList()
        .orEmpty()

    fun save(alarms: List<AlarmConfig>) {
        preferences.edit()
            .putString(KEY_ALARMS, alarms.joinToString("\n", transform = ::encode))
            .apply()
    }

    private fun encode(alarm: AlarmConfig): String = listOf(
        alarm.id,
        Uri.encode(alarm.name),
        alarm.hour,
        alarm.minute,
        alarm.weekdays.sorted().joinToString("."),
        alarm.enabled,
        alarm.routine.joinToString(",") { "${it.pose.name}:${it.durationSeconds}" },
        alarm.soundEnabled,
        alarm.vibrationEnabled,
        alarm.snoozeEnabled,
        alarm.sound.name,
        alarm.snoozeMinutes,
        alarm.snoozeCount,
    ).joinToString("|")

    private fun decode(encoded: String): AlarmConfig? = runCatching {
        val parts = encoded.split('|')
        AlarmConfig(
            id = parts[0].toLong(),
            name = Uri.decode(parts[1]),
            hour = parts[2].toInt(),
            minute = parts[3].toInt(),
            weekdays = parts[4].split('.').mapNotNull(String::toIntOrNull).toSet(),
            enabled = parts[5].toBooleanStrict(),
            routine = parts[6].split(',').map { step ->
                val (pose, duration) = step.split(':')
                PoseStep(YogaPose.valueOf(pose), duration.toInt())
            },
            soundEnabled = parts[7].toBooleanStrict(),
            vibrationEnabled = parts[8].toBooleanStrict(),
            snoozeEnabled = parts[9].toBooleanStrict(),
            sound = parts.getOrNull(10)?.let { AlarmSound.valueOf(it) } ?: AlarmSound.MORNING_BELLS,
            snoozeMinutes = parts.getOrNull(11)?.toIntOrNull() ?: 5,
            snoozeCount = parts.getOrNull(12)?.toIntOrNull() ?: 1,
        )
    }.getOrNull()

    private companion object {
        const val KEY_ALARMS = "alarms"
    }
}
