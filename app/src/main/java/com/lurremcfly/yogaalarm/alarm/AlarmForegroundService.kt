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
import androidx.core.app.NotificationCompat
import com.lurremcfly.yogaalarm.MainActivity
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
        audio?.close()
        audio = null
        vibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
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
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.name)
            .setContentText("Start your movement routine to stop the alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(openAlarm, true)
            .setContentIntent(openAlarm)
        if (remainingSnoozes > 0) {
            val snooze = PendingIntent.getBroadcast(
                this,
                alarmId.hashCode(),
                Intent(this, SnoozeReceiver::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            notificationBuilder.addAction(0, "Snooze ${alarm.snoozeMinutes} min", snooze)
        }
        val notification = notificationBuilder.build()
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
        audio?.close()
        audio = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
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
