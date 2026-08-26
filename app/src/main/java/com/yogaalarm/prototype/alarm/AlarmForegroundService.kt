package com.yogaalarm.prototype.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.yogaalarm.prototype.MainActivity
import com.yogaalarm.prototype.audio.PrototypeAlarmAudio
import com.yogaalarm.prototype.data.AlarmStore

class AlarmForegroundService : Service() {
    private var audio: PrototypeAlarmAudio? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Yoga Alarm wake-up alarms"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        val allowSnooze = intent?.getBooleanExtra(AlarmScheduler.EXTRA_ALLOW_SNOOZE, false) == true
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
                .putExtra(AlarmScheduler.EXTRA_ALLOW_SNOOZE, allowSnooze)
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
            .setSilent(true)
            .setFullScreenIntent(openAlarm, true)
            .setContentIntent(openAlarm)
        if (allowSnooze) {
            val snooze = PendingIntent.getBroadcast(
                this,
                alarmId.hashCode(),
                Intent(this, SnoozeReceiver::class.java).putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            notificationBuilder.addAction(0, "Snooze ${AlarmScheduler.SNOOZE_MINUTES} min", snooze)
        }
        val notification = notificationBuilder.build()
        startForeground(AlarmScheduler.NOTIFICATION_ID, notification)

        wakeLock?.release()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:active-alarm")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

        audio?.close()
        audio = if (alarm.soundEnabled) {
            PrototypeAlarmAudio(this, alarm.sound).also { it.start() }
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

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        audio?.close()
        audio = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "active_alarm_v1"
        const val WAKE_LOCK_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
