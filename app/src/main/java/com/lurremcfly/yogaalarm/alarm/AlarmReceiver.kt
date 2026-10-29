package com.lurremcfly.yogaalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lurremcfly.yogaalarm.data.AlarmStorageLock
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.data.WakeProgressStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        synchronized(AlarmStorageLock) {
            val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
            val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
            if (!isSnooze) {
                // Recover only locally decidable conversion. An interrupted checkout remains
                // pending until ownership resolves; a snooze always keeps its original snapshot.
                val trialStore = com.lurremcfly.yogaalarm.data.ProTrialStore(context)
                val trial = trialStore.load()
                if (trial.exhausted && trial.transition == com.lurremcfly.yogaalarm.model.FreeTransition.PENDING) {
                    trialStore.reconcile(paid = false, verifying = trial.awaitingPurchaseResolution, ownershipResolved = false)
                }
            }
            val store = AlarmStore(context)
            val savedAlarm = store.load().firstOrNull { it.id == alarmId } ?: return
            val occurrences = AlarmOccurrenceStore(context)
            val occurrence = if (isSnooze) {
                val token = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN) ?: return
                occurrences.find(token)?.takeIf { it.alarm.id == alarmId && !it.terminal }?.let {
                    occurrences.ringing(token)
                } ?: return
            } else {
                if (!savedAlarm.enabled) return
                occurrences.begin(savedAlarm, if (savedAlarm.snoozeEnabled) savedAlarm.snoozeCount else 0)
            }
            val alarm = occurrence.alarm
            WakeProgressStore(context).recordAlarmDue()
            val remainingSnoozes = occurrence.remainingSnoozes
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmForegroundService::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, occurrence.token)
                    .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes),
            )

            if (isSnooze) return
            if (alarm.weekdays.isEmpty()) {
                store.update { current -> current.map { if (it.id == alarm.id) it.copy(enabled = false) else it } }
            } else {
                AlarmScheduler.schedule(context, alarm, preserveSnooze = true)
            }
        }
    }
}
