package com.lurremcfly.yogaalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lurremcfly.yogaalarm.MainActivity
import com.lurremcfly.yogaalarm.data.WakeProgressStore
import com.lurremcfly.yogaalarm.model.AlarmConfig
import java.time.Instant
import java.time.Duration
import java.time.ZonedDateTime

object AlarmScheduler {
    fun schedule(context: Context, alarm: AlarmConfig, preserveSnooze: Boolean = false): Boolean {
        if (preserveSnooze) {
            context.getSystemService(AlarmManager::class.java).cancel(receiverIntent(context, alarm.id))
        } else {
            cancel(context, alarm.id)
        }
        if (!alarm.enabled) return false
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return false
        val operation = receiverIntent(context, alarm.id)
        // Use a separate action so notification updates cannot turn this shortcut into a ringing intent.
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val now = ZonedDateTime.now()
        val occurrence = nextOccurrence(alarm, now)
        val scheduled = runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(occurrence.toInstant().toEpochMilli(), showIntent),
                operation,
            )
        }.isSuccess
        if (scheduled) WakeProgressStore(context).recordScheduledAlarm(alarm, occurrence, now)
        return scheduled
    }

    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(receiverIntent(context, alarmId))
        alarmManager.cancel(snoozeReceiverIntent(context, alarmId))
    }

    fun scheduleSnooze(context: Context, alarmId: Long, minutes: Int, remainingSnoozes: Int): Boolean {
        val triggerAt = Instant.now().plusSeconds(minutes * 60L).toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return false
        val showIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                snoozeReceiverIntent(context, alarmId, remainingSnoozes),
            )
        }.isSuccess
    }

    private fun snoozeReceiverIntent(context: Context, alarmId: Long, remainingSnoozes: Int = 0) =
        PendingIntent.getBroadcast(
            context,
            alarmId.hashCode() xor SNOOZE_REQUEST_MASK,
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_IS_SNOOZE, true)
                .putExtra(EXTRA_REMAINING_SNOOZES, remainingSnoozes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun receiverIntent(context: Context, alarmId: Long) = PendingIntent.getBroadcast(
        context,
        alarmId.hashCode(),
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun nextOccurrence(alarm: AlarmConfig, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val today = now.toLocalDate().atTime(alarm.hour, alarm.minute)
        for (daysAhead in 0L..7L) {
            val candidate = ZonedDateTime.ofLocal(today.plusDays(daysAhead), now.zone, now.offset)
            if ((alarm.weekdays.isEmpty() || candidate.dayOfWeek.value in alarm.weekdays) && candidate.isAfter(now)) {
                return candidate
            }
        }
        return ZonedDateTime.ofLocal(today.plusWeeks(1), now.zone, now.offset)
    }

    internal fun confirmation(alarm: AlarmConfig, now: ZonedDateTime = ZonedDateTime.now()): String {
        val minutes = (Duration.between(now, nextOccurrence(alarm, now)).seconds + 59) / 60
        val hours = minutes / 60
        return if (hours > 0) "Alarm in $hours h ${minutes % 60} min" else "Alarm in $minutes min"
    }

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_REMAINING_SNOOZES = "remaining_snoozes"
    const val EXTRA_IS_SNOOZE = "is_snooze"
    const val NOTIFICATION_ID = 7001

    private const val SNOOZE_REQUEST_MASK = 0x51F00E
}
