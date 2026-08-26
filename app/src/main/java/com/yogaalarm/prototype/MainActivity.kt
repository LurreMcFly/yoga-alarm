package com.yogaalarm.prototype

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.core.app.NotificationManagerCompat
import com.yogaalarm.prototype.data.AlarmStore
import com.yogaalarm.prototype.alarm.AlarmForegroundService
import com.yogaalarm.prototype.alarm.AlarmScheduler
import com.yogaalarm.prototype.model.AlarmConfig
import com.yogaalarm.prototype.ui.AlarmEditorScreen
import com.yogaalarm.prototype.ui.AlarmHomeScreen
import com.yogaalarm.prototype.ui.RoutineCameraScreen
import com.yogaalarm.prototype.ui.RoutineUiState
import com.yogaalarm.prototype.ui.RoutineViewModel

class MainActivity : ComponentActivity() {
    private val firedAlarmId = mutableStateOf<Long?>(null)
    private val firedAlarmCanSnooze = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firedAlarmId.value = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L).takeIf { it >= 0L }
        firedAlarmCanSnooze.value = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALLOW_SNOOZE, true)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            YogaAlarmTheme {
                YogaAlarmRoot(
                    firedAlarmId = firedAlarmId.value,
                    firedAlarmCanSnooze = firedAlarmCanSnooze.value,
                    onFiredAlarmHandled = { firedAlarmId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        firedAlarmId.value = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L).takeIf { it >= 0L }
        firedAlarmCanSnooze.value = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALLOW_SNOOZE, true)
    }
}

@Composable
private fun YogaAlarmRoot(
    firedAlarmId: Long?,
    firedAlarmCanSnooze: Boolean,
    onFiredAlarmHandled: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { AlarmStore(context.applicationContext) }
    var alarms by remember { mutableStateOf(store.load()) }
    var route by rememberSaveable { mutableStateOf("home") }
    var editingAlarmId by rememberSaveable { mutableStateOf(-1L) }
    var pendingEditorAlarm by remember { mutableStateOf<AlarmConfig?>(null) }
    var firedRoutine by rememberSaveable { mutableStateOf(false) }
    var snoozeAvailable by rememberSaveable { mutableStateOf(false) }
    var readinessRefresh by remember { mutableStateOf(0) }
    val permissionPreferences = remember {
        context.getSharedPreferences("alarm_readiness", android.content.Context.MODE_PRIVATE)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { readinessRefresh += 1 }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { readinessRefresh += 1 }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { readinessRefresh += 1 }
    val cameraReady = remember(readinessRefresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val notificationsReady = remember(readinessRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val fullScreenReady = remember(readinessRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val exactAlarmsReady = remember(readinessRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    LaunchedEffect(Unit) {
        alarms.filter { it.enabled }.forEach { AlarmScheduler.schedule(context, it) }
    }
    LaunchedEffect(firedAlarmId) {
        val alarmId = firedAlarmId ?: return@LaunchedEffect
        alarms = store.load()
        val fired = alarms.firstOrNull { it.id == alarmId } ?: return@LaunchedEffect
        editingAlarmId = fired.id
        pendingEditorAlarm = fired
        firedRoutine = true
        snoozeAvailable = firedAlarmCanSnooze && fired.snoozeEnabled
        route = "camera"
        onFiredAlarmHandled()
    }

    when (route) {
        "editor" -> {
            val initial = pendingEditorAlarm
                ?: alarms.firstOrNull { it.id == editingAlarmId }
                ?: AlarmConfig.create(editingAlarmId)
            AlarmEditorScreen(
                initialAlarm = initial,
                onCancel = {
                    pendingEditorAlarm = null
                    route = "home"
                },
                onSave = { saved ->
                    alarms = if (alarms.any { it.id == saved.id }) {
                        alarms.map { if (it.id == saved.id) saved else it }
                    } else {
                        alarms + saved
                    }
                    store.save(alarms)
                    AlarmScheduler.schedule(context, saved)
                    pendingEditorAlarm = null
                    route = "home"
                },
                onTestRoutine = { draft ->
                    pendingEditorAlarm = draft
                    firedRoutine = false
                    route = "camera"
                },
            )
        }
        "camera" -> {
            val testAlarm = pendingEditorAlarm ?: AlarmConfig.create(editingAlarmId)
            val viewModel: RoutineViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            CameraPermissionRoute(
                alarm = testAlarm,
                uiState = uiState,
                onPoseFrame = viewModel::onPoseFrame,
                onCameraError = viewModel::onCameraError,
                onStartRoutine = viewModel::start,
                isFiredAlarm = firedRoutine,
                snoozeAvailable = snoozeAvailable,
                onSnooze = {
                    context.stopService(Intent(context, AlarmForegroundService::class.java))
                    AlarmScheduler.scheduleSnooze(context, testAlarm.id)
                    firedRoutine = false
                    snoozeAvailable = false
                    pendingEditorAlarm = null
                    route = "home"
                },
                onBack = {
                    if (firedRoutine) {
                        context.stopService(Intent(context, AlarmForegroundService::class.java))
                        firedRoutine = false
                        snoozeAvailable = false
                        pendingEditorAlarm = null
                        route = "home"
                    } else {
                        route = "editor"
                    }
                },
            )
        }
        else -> AlarmHomeScreen(
            alarms = alarms,
            cameraReady = cameraReady,
            notificationsReady = notificationsReady,
            fullScreenReady = fullScreenReady,
            exactAlarmsReady = exactAlarmsReady,
            onFixCamera = {
                val activity = context as? Activity
                val requested = permissionPreferences.getBoolean("camera_requested", false)
                if (requested && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                } else {
                    permissionPreferences.edit().putBoolean("camera_requested", true).apply()
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onFixNotifications = {
                val activity = context as? Activity
                val requested = permissionPreferences.getBoolean("notifications_requested", false)
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requested) ||
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                ) {
                    permissionPreferences.edit().putBoolean("notifications_requested", true).apply()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
            onFixFullScreen = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            },
            onFixExactAlarms = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            },
            onAddAlarm = {
                editingAlarmId = System.currentTimeMillis()
                pendingEditorAlarm = null
                route = "editor"
            },
            onEditAlarm = {
                editingAlarmId = it
                pendingEditorAlarm = null
                route = "editor"
            },
            onToggleAlarm = { id, enabled ->
                alarms = alarms.map { if (it.id == id) it.copy(enabled = enabled) else it }
                store.save(alarms)
                alarms.firstOrNull { it.id == id }?.let { AlarmScheduler.schedule(context, it) }
            },
        )
    }
}

@Composable
private fun CameraPermissionRoute(
    alarm: AlarmConfig,
    uiState: RoutineUiState,
    onPoseFrame: (com.yogaalarm.prototype.model.PoseFrame) -> Unit,
    onCameraError: (String) -> Unit,
    onStartRoutine: (AlarmConfig) -> Unit,
    isFiredAlarm: Boolean,
    snoozeAvailable: Boolean,
    onSnooze: () -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cameraStarted by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            if (isFiredAlarm) {
                context.stopService(Intent(context, AlarmForegroundService::class.java))
            }
            onStartRoutine(alarm)
            cameraStarted = true
        }
    }
    BackHandler(enabled = isFiredAlarm) { }

    if (cameraStarted && hasPermission) {
        RoutineCameraScreen(
            uiState = uiState,
            onPoseFrame = onPoseFrame,
            onCameraError = onCameraError,
            showBack = !isFiredAlarm,
            onBack = onBack,
        )
    } else {
        CameraReadyScreen(
            permissionDenied = permissionDenied,
            showBack = !isFiredAlarm,
            showSnooze = isFiredAlarm && snoozeAvailable,
            showEmergencyStop = isFiredAlarm && permissionDenied,
            onSnooze = onSnooze,
            onStartCamera = {
                if (hasPermission) {
                    if (isFiredAlarm) {
                        context.stopService(Intent(context, AlarmForegroundService::class.java))
                    }
                    onStartRoutine(alarm)
                    cameraStarted = true
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun CameraReadyScreen(
    permissionDenied: Boolean,
    showBack: Boolean,
    showSnooze: Boolean,
    showEmergencyStop: Boolean,
    onSnooze: () -> Unit,
    onStartCamera: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101511))
            .padding(24.dp),
    ) {
        if (showBack) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            ) {
                Text("‹ Back", color = Color.White)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (permissionDenied) "Camera permission is needed" else "Move when you’re ready",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (permissionDenied) {
                    "Allow camera access to validate your poses."
                } else {
                    "Get dressed and place your phone before starting the camera."
                },
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onStartCamera) {
                Text(if (permissionDenied) "Allow camera" else "Start camera")
            }
            if (showSnooze) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSnooze) {
                    Text("Snooze ${AlarmScheduler.SNOOZE_MINUTES} minutes", color = Color.White)
                }
            }
            if (showEmergencyStop) {
                TextButton(onClick = onBack) {
                    Text("Stop alarm", color = Color.White.copy(alpha = 0.72f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Camera off", color = Color.White.copy(alpha = 0.82f))
            Spacer(Modifier.height(5.dp))
            Text(
                "Processed on-device · Never recorded",
                color = Color.White.copy(alpha = 0.52f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun YogaAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF274D34),
            onPrimary = Color.White,
            secondary = Color(0xFFBDEC68),
            surface = Color(0xFFF8FBF7),
            onSurface = Color(0xFF142018),
        ),
        content = content,
    )
}
