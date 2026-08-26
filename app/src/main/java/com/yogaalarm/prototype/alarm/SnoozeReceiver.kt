package com.yogaalarm.prototype.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (alarmId < 0L) return
        context.stopService(Intent(context, AlarmForegroundService::class.java))
        AlarmScheduler.scheduleSnooze(context, alarmId)
    }
}
