package com.yogaalarm.prototype.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.yogaalarm.prototype.MainActivity
import com.yogaalarm.prototype.model.AlarmConfig
import java.time.Instant
import java.time.ZonedDateTime

object AlarmScheduler {
    fun schedule(context: Context, alarm: AlarmConfig) {
        cancel(context, alarm.id)
        if (!alarm.enabled) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = receiverIntent(context, alarm.id)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ALARM_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(nextOccurrence(alarm).toInstant().toEpochMilli(), showIntent),
            operation,
        )
    }

    fun cancel(context: Context, alarmId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(receiverIntent(context, alarmId))
    }

    fun scheduleSnooze(context: Context, alarmId: Long, minutes: Int, remainingSnoozes: Int) {
        val triggerAt = Instant.now().plusSeconds(minutes * 60L).toEpochMilli()
        val showIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_REMAINING_SNOOZES, remainingSnoozes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java).setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            PendingIntent.getBroadcast(
                context,
                alarmId.hashCode() xor SNOOZE_REQUEST_MASK,
                Intent(context, AlarmReceiver::class.java)
                    .putExtra(EXTRA_ALARM_ID, alarmId)
                    .putExtra(EXTRA_IS_SNOOZE, true)
                    .putExtra(EXTRA_REMAINING_SNOOZES, remainingSnoozes),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun receiverIntent(context: Context, alarmId: Long) = PendingIntent.getBroadcast(
        context,
        alarmId.hashCode(),
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextOccurrence(alarm: AlarmConfig, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val today = now.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)
        if (alarm.weekdays.isEmpty()) return if (today.isAfter(now)) today else today.plusDays(1)
        for (daysAhead in 0L..7L) {
            val candidate = today.plusDays(daysAhead)
            if (candidate.dayOfWeek.value in alarm.weekdays && candidate.isAfter(now)) return candidate
        }
        return today.plusWeeks(1)
    }

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_REMAINING_SNOOZES = "remaining_snoozes"
    const val EXTRA_IS_SNOOZE = "is_snooze"
    const val NOTIFICATION_ID = 7001

    private const val SNOOZE_REQUEST_MASK = 0x51F00E
}
