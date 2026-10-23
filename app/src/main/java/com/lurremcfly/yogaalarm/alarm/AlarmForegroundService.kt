package com.lurremcfly.yogaalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Binder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.lurremcfly.yogaalarm.MainActivity
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.audio.AlarmAudio
import com.lurremcfly.yogaalarm.data.AlarmStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmForegroundService : Service() {
    private var audio: AlarmAudio? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeAlarmId: Long? = null
    private val mutableFinishedAlarmId = MutableStateFlow<Long?>(null)
    val finishedAlarmId = mutableFinishedAlarmId.asStateFlow()

    inner class LocalBinder : Binder() {
        val service: AlarmForegroundService get() = this@AlarmForegroundService
    }

    fun setRoutineLevel(alarmId: Long, level: Float) {
        if (activeAlarmId == alarmId) audio?.setLevel(level)
    }

    fun finishRoutine(alarmId: Long) {
        if (activeAlarmId != alarmId) return
        releasePlaybackResources()
        activeAlarmId = null
        mutableRingingAlarm.value = null
        mutableFinishedAlarmId.value = alarmId
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        if (intent?.action == ACTION_STOP_ALARM) {
            finishRoutine(alarmId)
            if (activeAlarmId == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        // Reattaching a recreated camera screen must not restart the ringing track.
        if (activeAlarmId == alarmId) return START_REDELIVER_INTENT
        val remainingSnoozes = intent?.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, 0) ?: 0
        val alarm = AlarmStore(this).load().firstOrNull { it.id == alarmId }
        if (alarm == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val openAlarm = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            Intent(this, MainActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAlarm = if (!alarm.stopButtonDisabled) {
            PendingIntent.getBroadcast(
                this,
                alarmId.hashCode(),
                Intent(this, StopAlarmReceiver::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }
        val snooze = if (alarm.snoozeEnabled && remainingSnoozes > 0) {
            PendingIntent.getBroadcast(
                this,
                alarmId.hashCode(),
                Intent(this, SnoozeReceiver::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }
        val compactContent = RemoteViews(packageName, R.layout.notification_alarm_compact)
        val expandedContent = RemoteViews(packageName, R.layout.notification_alarm_expanded).apply {
            setTextViewText(R.id.notification_alarm_name, alarm.name)
        }
        val headsUpContent = RemoteViews(packageName, R.layout.notification_alarm_heads_up)
        for (content in listOf(compactContent, expandedContent, headsUpContent)) {
            content.setOnClickPendingIntent(R.id.notification_start, openAlarm)
            content.setViewVisibility(R.id.notification_stop_container, if (stopAlarm != null) View.VISIBLE else View.GONE)
            content.setViewVisibility(R.id.notification_snooze_container, if (snooze != null) View.VISIBLE else View.GONE)
            if (stopAlarm != null) content.setOnClickPendingIntent(R.id.notification_stop, stopAlarm)
            if (snooze != null) {
                content.setTextViewText(R.id.notification_snooze, getString(R.string.notification_snooze, alarm.snoozeMinutes))
                content.setOnClickPendingIntent(R.id.notification_snooze, snooze)
            }
        }
        if (stopAlarm == null && snooze == null) {
            expandedContent.setViewVisibility(R.id.notification_secondary_actions, View.GONE)
            headsUpContent.setViewVisibility(R.id.notification_secondary_actions, View.GONE)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(0xFF274D34.toInt())
            .setContentTitle(alarm.name)
            .setContentText(getString(R.string.notification_time_to_move))
            .setSubText(alarm.name)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compactContent)
            .setCustomBigContentView(expandedContent)
            // Supply controls in the initial detailed popup; the OS still controls expansion.
            .setCustomHeadsUpContentView(headsUpContent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(openAlarm, true)
            .setContentIntent(openAlarm)
            .build()
        startForeground(AlarmScheduler.NOTIFICATION_ID, notification)
        activeAlarmId = alarmId
        mutableFinishedAlarmId.value = null

        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:active-alarm")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

        audio?.close()
        audio = if (alarm.soundEnabled) {
            AlarmAudio(this, alarm.sound).also { it.start() }
        } else {
            null
        }

        vibrator?.cancel()
        if (alarm.vibrationEnabled) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 350, 500), 0))
        }

        mutableRingingAlarm.value = alarmId to remainingSnoozes
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        mutableRingingAlarm.value = null
        releasePlaybackResources()
        super.onDestroy()
    }

    private fun releasePlaybackResources() {
        audio?.close()
        audio = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    companion object {
        private val mutableRingingAlarm = MutableStateFlow<Pair<Long, Int>?>(null)
        val ringingAlarm = mutableRingingAlarm.asStateFlow()

        const val ACTION_STOP_ALARM = "com.lurremcfly.yogaalarm.STOP_ALARM"
        const val CHANNEL_ID = "active_alarm_v2"

        fun ensureNotificationChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Active alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Yoga Alarm wake-up alarms"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                },
            )
        }

        private const val WAKE_LOCK_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
