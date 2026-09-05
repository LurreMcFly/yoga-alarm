package com.lurremcfly.yogaalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lurremcfly.yogaalarm.data.AlarmStore

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val remainingSnoozes = intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, 0)
        if (alarmId < 0L || remainingSnoozes <= 0) return
        val alarm = AlarmStore(context).load().firstOrNull { it.id == alarmId } ?: return
        val scheduled = AlarmScheduler.scheduleSnooze(
            context = context,
            alarmId = alarmId,
            minutes = alarm.snoozeMinutes,
            remainingSnoozes = (remainingSnoozes - 1).coerceAtLeast(0),
        )
        if (scheduled) {
            // stopService alone does not destroy a service while the camera UI is bound.
            context.startService(
                Intent(context, AlarmForegroundService::class.java)
                    .setAction(AlarmForegroundService.ACTION_STOP_ALARM)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId),
            )
        }
    }
}
