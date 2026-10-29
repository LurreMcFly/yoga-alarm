package com.lurremcfly.yogaalarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lurremcfly.yogaalarm.alarm.AlarmForegroundService
import com.lurremcfly.yogaalarm.alarm.AlarmScheduler
import com.lurremcfly.yogaalarm.ui.YogaAlarmRoot
import com.lurremcfly.yogaalarm.ui.YogaAlarmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val firedOccurrenceToken = mutableStateOf<String?>(null)
    private val firedAlarmId = mutableStateOf<Long?>(null)
    private val cameraStartOccurrenceToken = mutableStateOf<String?>(null)
    private val cameraStartAlarmId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmForegroundService.ensureNotificationChannel(this)
        firedOccurrenceToken.value = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN)
        updateAlarmWindowState(intent)
        // Compose restores the current route on recreation; don't replay the launch alarm.
        if (savedInstanceState == null) {
            firedAlarmId.value = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L).takeIf { it >= 0L }
        }
        cameraStartAlarmId.value = if (savedInstanceState != null) {
            savedInstanceState.getLong(STATE_CAMERA_START_ALARM_ID, -1L).takeIf { it >= 0L }
        } else {
            firedAlarmId.value.takeIf { intent.action == AlarmScheduler.ACTION_START_CAMERA }
        }
        cameraStartOccurrenceToken.value = if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_CAMERA_START_OCCURRENCE_TOKEN)
        } else firedOccurrenceToken.value.takeIf { intent.action == AlarmScheduler.ACTION_START_CAMERA }
        // Screen backgrounds supply the bar colors, including on Android 8–14.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AlarmForegroundService.ringingAlarm.collect { ringing ->
                    if (ringing != null) {
                        setAlarmPresentation(true)
                        firedOccurrenceToken.value = AlarmForegroundService.ringingOccurrenceToken.value
                        firedAlarmId.value = ringing.first
                        if (cameraStartOccurrenceToken.value != firedOccurrenceToken.value) {
                            cameraStartAlarmId.value = null
                            cameraStartOccurrenceToken.value = null
                        }
                    }
                }
            }
        }
        setContent {
            YogaAlarmTheme {
                YogaAlarmRoot(
                    firedAlarmId = firedAlarmId.value,
                    firedOccurrenceToken = firedOccurrenceToken.value,
                    cameraStartAlarmId = cameraStartAlarmId.value.takeIf {
                        cameraStartOccurrenceToken.value == firedOccurrenceToken.value
                    },
                    onCameraStartHandled = { alarmId ->
                        if (cameraStartAlarmId.value == alarmId) {
                            cameraStartAlarmId.value = null
                            cameraStartOccurrenceToken.value = null
                        }
                    },
                    onFiredAlarmHandled = { firedAlarmId.value = null },
                    onAlarmPresentationFinished = ::clearAlarmWindowState,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        firedOccurrenceToken.value = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN)
        updateAlarmWindowState(intent)
        firedAlarmId.value = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L).takeIf { it >= 0L }
        if (intent.action == AlarmScheduler.ACTION_START_CAMERA) {
            cameraStartAlarmId.value = firedAlarmId.value
            cameraStartOccurrenceToken.value = firedOccurrenceToken.value
        } else if (cameraStartOccurrenceToken.value != firedOccurrenceToken.value) {
            cameraStartAlarmId.value = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CAMERA_START_OCCURRENCE_TOKEN, cameraStartOccurrenceToken.value)
        outState.putLong(STATE_CAMERA_START_ALARM_ID, cameraStartAlarmId.value ?: -1L)
    }

    private fun updateAlarmWindowState(intent: Intent) {
        val isAlarm = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) >= 0L
        setAlarmPresentation(isAlarm)
    }

    private fun clearAlarmWindowState() {
        cameraStartOccurrenceToken.value = null
        cameraStartAlarmId.value = null
        intent.removeExtra(AlarmScheduler.EXTRA_ALARM_ID)
        setAlarmPresentation(false)
    }

    @Suppress("DEPRECATION")
    private fun setAlarmPresentation(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enabled) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    companion object {
        private const val STATE_CAMERA_START_OCCURRENCE_TOKEN = "camera_start_occurrence_token"
        private const val STATE_CAMERA_START_ALARM_ID = "camera_start_alarm_id"
    }
}
