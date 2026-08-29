package com.yogaalarm.prototype.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yogaalarm.prototype.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val store = AlarmStore(context)
        val alarm = store.load().firstOrNull { it.id == alarmId } ?: return
        val remainingSnoozes = if (isSnooze) {
            intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, 0)
        } else if (alarm.snoozeEnabled) {
            alarm.snoozeCount
        } else {
            0
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmForegroundService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes),
        )

        if (isSnooze) return
        if (alarm.weekdays.isEmpty()) {
            store.save(store.load().map { if (it.id == alarm.id) it.copy(enabled = false) else it })
        } else {
            AlarmScheduler.schedule(context, alarm)
        }
    }
}
