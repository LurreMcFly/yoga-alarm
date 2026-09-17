package com.lurremcfly.yogaalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lurremcfly.yogaalarm.data.AlarmStore

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val alarm = AlarmStore(context).load().firstOrNull { it.id == alarmId } ?: return
        if (alarm.stopButtonDisabled) return

        context.startService(
            Intent(context, AlarmForegroundService::class.java)
                .setAction(AlarmForegroundService.ACTION_STOP_ALARM)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId),
        )
    }
}
