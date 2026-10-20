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
    private val firedAlarmId = mutableStateOf<Long?>(null)
    private val firedAlarmRemainingSnoozes = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmForegroundService.ensureNotificationChannel(this)
        updateAlarmWindowState(intent)
        // Compose restores the current route on recreation; don't replay the launch alarm.
        if (savedInstanceState == null) {
            firedAlarmId.value = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L).takeIf { it >= 0L }
            firedAlarmRemainingSnoozes.value = intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, 0)
        }
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
                        firedAlarmRemainingSnoozes.value = ringing.second
                        firedAlarmId.value = ringing.first
                    }
                }
            }
        }
        setContent {
            YogaAlarmTheme {
                YogaAlarmRoot(
                    firedAlarmId = firedAlarmId.value,
                    firedAlarmRemainingSnoozes = firedAlarmRemainingSnoozes.value,
                    onFiredAlarmHandled = { firedAlarmId.value = null },
                    onAlarmPresentationFinished = ::clearAlarmWindowState,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateAlarmWindowState(intent)
        firedAlarmId.value = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L).takeIf { it >= 0L }
        firedAlarmRemainingSnoozes.value = intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, 0)
    }

    private fun updateAlarmWindowState(intent: Intent) {
        val isAlarm = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) >= 0L
        setAlarmPresentation(isAlarm)
    }

    private fun clearAlarmWindowState() {
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
}
