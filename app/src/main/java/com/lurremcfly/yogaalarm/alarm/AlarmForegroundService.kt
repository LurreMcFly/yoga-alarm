package com.lurremcfly.yogaalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lurremcfly.yogaalarm.MainActivity
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.audio.AlarmAudio
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore
import com.lurremcfly.yogaalarm.model.OccurrenceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

class AlarmForegroundService : Service() {
    private var audio: AlarmAudio? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeAlarmId: Long? = null
    private var activeOccurrenceToken: String? = null
    private var activeNotificationBuilder: NotificationCompat.Builder? = null
    private val handler = Handler(Looper.getMainLooper())
    private var inactivityTimeout: Runnable? = null
    private var lastActivityAtMs = 0L
    private val notificationDismissedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
            if (intent.action != ACTION_NOTIFICATION_DISMISSED) return
            // Android 14+ allows ongoing notifications to be swiped away while unlocked.
            // Restore only the active alarm's controls, without replaying its alert or UI.
            dismissNotificationPopup(alarmId, intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN))
        }
    }
    private val mutableFinishedAlarmId = MutableStateFlow<Long?>(null)
    val finishedAlarmId = mutableFinishedAlarmId.asStateFlow()

    inner class LocalBinder : Binder() {
        val service: AlarmForegroundService get() = this@AlarmForegroundService
    }

    fun setRoutineLevel(alarmId: Long, level: Float, occurrenceToken: String? = activeOccurrenceToken) {
        if (activeAlarmId == alarmId && activeOccurrenceToken == occurrenceToken) audio?.setLevel(level)
    }

    fun recordActivity(alarmId: Long, occurrenceToken: String? = activeOccurrenceToken) {
        if (activeAlarmId == alarmId && activeOccurrenceToken == occurrenceToken) lastActivityAtMs = SystemClock.elapsedRealtime()
    }

    fun dismissNotificationPopup(alarmId: Long, occurrenceToken: String? = activeOccurrenceToken) {
        if (activeAlarmId != alarmId || activeOccurrenceToken != occurrenceToken) return
        // Keep foreground playback and shade controls, but remove the heads-up alert.
        val notification = activeNotificationBuilder
            ?.setFullScreenIntent(null, false)
            ?.setSilent(true)
            ?.setOnlyAlertOnce(true)
            ?.build() ?: return
        getSystemService(NotificationManager::class.java).notify(AlarmScheduler.NOTIFICATION_ID, notification)
    }

    fun finishRoutine(alarmId: Long, occurrenceToken: String? = activeOccurrenceToken, persistStop: Boolean = true) {
        if (activeAlarmId != alarmId || activeOccurrenceToken != occurrenceToken) return
        releasePlaybackResources()
        if (persistStop) activeOccurrenceToken?.let { AlarmOccurrenceStore(this).stop(it) }
        activeAlarmId = null
        activeNotificationBuilder = null
        mutableRingingAlarm.value = null
        mutableRingingOccurrenceToken.value = null
        mutableFinishedAlarmId.value = alarmId
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
        ContextCompat.registerReceiver(
            this,
            notificationDismissedReceiver,
            IntentFilter(ACTION_NOTIFICATION_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val occurrenceToken = intent?.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN)
        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        if (intent?.action == ACTION_STOP_ALARM) {
            finishRoutine(alarmId, occurrenceToken, persistStop = intent?.getBooleanExtra(EXTRA_PRESERVE_OCCURRENCE, false) != true)
            if (activeAlarmId == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        // Reattaching a recreated camera screen must not restart the ringing track.
        if (activeAlarmId == alarmId && activeOccurrenceToken == occurrenceToken) return START_REDELIVER_INTENT
        val occurrence = AlarmOccurrenceStore(this).find(occurrenceToken)
        val alarm = occurrence?.alarm
        if (alarm == null || occurrence?.state != OccurrenceState.RINGING || alarm.id != alarmId) {
            if (activeAlarmId == null) stopSelf(startId)
            return START_NOT_STICKY
        }

        val remainingSnoozes = occurrence.remainingSnoozes
        val openAlarmIntent = Intent(this, MainActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, occurrenceToken)
            .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openAlarm = PendingIntent.getActivity(
            this,
            occurrenceToken.hashCode(),
            openAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val startCamera = PendingIntent.getActivity(
            this,
            occurrenceToken.hashCode(),
            Intent(openAlarmIntent).setAction(AlarmScheduler.ACTION_START_CAMERA),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAlarm = if (!alarm.stopButtonDisabled) {
            PendingIntent.getBroadcast(
                this,
                occurrenceToken.hashCode(),
                Intent(this, StopAlarmReceiver::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, occurrenceToken),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }
        val snooze = if (alarm.snoozeEnabled && remainingSnoozes > 0) {
            PendingIntent.getBroadcast(
                this,
                occurrenceToken.hashCode(),
                Intent(this, SnoozeReceiver::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, occurrenceToken)
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
            content.setOnClickPendingIntent(R.id.notification_start, startCamera)
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
        } else {
            val singleAction = stopAlarm == null || snooze == null
            for (content in listOf(expandedContent, headsUpContent)) {
                content.setViewVisibility(R.id.notification_secondary_gap, if (singleAction) View.GONE else View.VISIBLE)
                content.setViewVisibility(R.id.notification_secondary_spacer_start, if (singleAction) View.VISIBLE else View.GONE)
                content.setViewVisibility(R.id.notification_secondary_spacer_end, if (singleAction) View.VISIBLE else View.GONE)
            }
        }
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
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
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    this,
                    occurrenceToken.hashCode(),
                    Intent(ACTION_NOTIFICATION_DISMISSED)
                        .setPackage(packageName)
                        .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, occurrenceToken),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setFullScreenIntent(openAlarm, true)
            .setContentIntent(openAlarm)
        activeOccurrenceToken?.takeIf { it != occurrenceToken }?.let {
            AlarmOccurrenceStore(this).stop(it, onlyIfRinging = true)
        }
        activeNotificationBuilder = notificationBuilder
        activeAlarmId = alarmId
        activeOccurrenceToken = occurrenceToken
        mutableRingingOccurrenceToken.value = occurrenceToken
        startForeground(AlarmScheduler.NOTIFICATION_ID, notificationBuilder.build())
        mutableFinishedAlarmId.value = null

        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:active-alarm")
            .apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }

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

        inactivityTimeout?.let(handler::removeCallbacks)
        val startedAt = System.currentTimeMillis()
        val startedAtElapsed = SystemClock.elapsedRealtime()
        lastActivityAtMs = startedAtElapsed
        val timeout = object : Runnable {
            override fun run() {
                if (activeAlarmId != alarmId || activeOccurrenceToken != occurrenceToken) return
                val now = SystemClock.elapsedRealtime()
                val remaining = INACTIVITY_TIMEOUT_MS - (now - lastActivityAtMs)
                if (remaining > 0L) {
                    wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
                    handler.postDelayed(this, remaining)
                    return
                }
                val endedAt = System.currentTimeMillis()
                val timeFormat = DateFormat.getTimeFormat(this@AlarmForegroundService)
                val message = getString(
                    R.string.notification_missed_alarm_detail,
                    (now - startedAtElapsed) / 60_000L,
                    timeFormat.format(Date(startedAt)),
                    timeFormat.format(Date(endedAt)),
                )
                finishRoutine(alarmId, occurrenceToken)
                val openApp = PendingIntent.getActivity(
                    this@AlarmForegroundService,
                    0,
                    Intent(this@AlarmForegroundService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(this@AlarmForegroundService, MISSED_ALARM_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle(getString(R.string.notification_missed_alarm_title, alarm.name))
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .setSilent(true)
                    .setWhen(endedAt)
                    .build()
                getSystemService(NotificationManager::class.java).notify(alarmId.toString(), MISSED_ALARM_NOTIFICATION_ID, notification)
            }
        }
        inactivityTimeout = timeout
        handler.postDelayed(timeout, INACTIVITY_TIMEOUT_MS)
        mutableRingingAlarm.value = alarmId to remainingSnoozes
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        unregisterReceiver(notificationDismissedReceiver)
        activeNotificationBuilder = null
        activeAlarmId = null
        mutableRingingAlarm.value = null
        mutableRingingOccurrenceToken.value = null
        releasePlaybackResources()
        super.onDestroy()
    }

    private fun releasePlaybackResources() {
        inactivityTimeout?.let(handler::removeCallbacks)
        inactivityTimeout = null
        audio?.close()
        audio = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    companion object {
        private val mutableRingingOccurrenceToken = MutableStateFlow<String?>(null)
        val ringingOccurrenceToken = mutableRingingOccurrenceToken.asStateFlow()
        const val EXTRA_PRESERVE_OCCURRENCE = "preserve_occurrence"
        private val mutableRingingAlarm = MutableStateFlow<Pair<Long, Int>?>(null)
        val ringingAlarm = mutableRingingAlarm.asStateFlow()

        const val ACTION_STOP_ALARM = "com.lurremcfly.yogaalarm.STOP_ALARM"
        private const val ACTION_NOTIFICATION_DISMISSED = "com.lurremcfly.yogaalarm.NOTIFICATION_DISMISSED"
        const val CHANNEL_ID = "active_alarm_v2"
        private const val MISSED_ALARM_CHANNEL_ID = "missed_alarms"
        private const val MISSED_ALARM_NOTIFICATION_ID = 7002

        fun ensureNotificationChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Active alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Yoga Alarm wake-up alarms"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                },
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(MISSED_ALARM_CHANNEL_ID, "Missed alarms", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Alarms stopped after ten minutes without activity"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }

        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
