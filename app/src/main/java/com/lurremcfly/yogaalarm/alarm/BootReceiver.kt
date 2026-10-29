package com.lurremcfly.yogaalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AlarmOccurrenceStore(context).clearSnoozes()
        AlarmStore(context).load().filter { it.enabled }.forEach {
            AlarmScheduler.schedule(context, it, preserveSnooze = true)
        }
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
