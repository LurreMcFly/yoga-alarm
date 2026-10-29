package com.lurremcfly.yogaalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val token = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN) ?: return
        if (AlarmForegroundService.ringingOccurrenceToken.value != token) return
        val occurrence = AlarmOccurrenceStore(context).find(token)?.takeUnless { it.terminal } ?: return
        val alarm = occurrence.alarm
        if (alarm.stopButtonDisabled) return

        context.startService(
            Intent(context, AlarmForegroundService::class.java)
                .setAction(AlarmForegroundService.ACTION_STOP_ALARM)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, token),
        )
    }
}
