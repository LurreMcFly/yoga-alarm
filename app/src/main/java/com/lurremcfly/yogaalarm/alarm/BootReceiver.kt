package com.lurremcfly.yogaalarm.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AlarmOccurrenceStore(context).clearSnoozes()
        val store = AlarmStore(context)
        store.load().filter { it.enabled || it.scheduleNeedsRepair }.forEach {
            store.schedule(it, retry = it.scheduleNeedsRepair)
        }
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
