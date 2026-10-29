package com.lurremcfly.yogaalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore
import com.lurremcfly.yogaalarm.data.AlarmStore

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val remainingSnoozes = intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, 0)
        if (alarmId < 0L || remainingSnoozes <= 0) return
        // A queued notification action must not revive a stopped/replaced alarm or reuse spent snoozes.
        if (AlarmForegroundService.ringingAlarm.value != (alarmId to remainingSnoozes)) return
        val token = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN) ?: return
        if (AlarmForegroundService.ringingOccurrenceToken.value != token) return
        val occurrence = AlarmOccurrenceStore(context).find(token)?.takeUnless { it.terminal } ?: return
        val alarm = occurrence.alarm
        if (!alarm.snoozeEnabled || AlarmStore(context).load().none { it.id == alarmId && it.snoozeEnabled }) return
        val scheduled = AlarmScheduler.scheduleSnooze(
            context = context,
            alarmId = alarmId,
            minutes = alarm.snoozeMinutes,
            remainingSnoozes = (remainingSnoozes - 1).coerceAtLeast(0),
            occurrenceToken = token,
        )
        if (scheduled) {
            // stopService alone does not destroy a service while the camera UI is bound.
            context.startService(
                Intent(context, AlarmForegroundService::class.java)
                    .setAction(AlarmForegroundService.ACTION_STOP_ALARM)
                    .putExtra(AlarmForegroundService.EXTRA_PRESERVE_OCCURRENCE, true)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, token),
            )
        }
    }
}
