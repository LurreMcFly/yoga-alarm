package com.lurremcfly.yogaalarm.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.alarm.AlarmForegroundService
import com.lurremcfly.yogaalarm.alarm.AlarmScheduler
import com.lurremcfly.yogaalarm.audio.AlarmAudio
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore
import java.time.ZonedDateTime
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.OccurrenceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun CameraPermissionRoute(
    alarm: AlarmConfig,
    viewModel: RoutineViewModel,
    onRoutineComplete: (String?, ZonedDateTime) -> Unit,
    occurrenceToken: String? = null,
    onCompletionPresentationFinished: () -> Unit,
    isFiredAlarm: Boolean,
    cameraStartRequested: Boolean,
    onCameraStartHandled: () -> Unit,
    remainingSnoozes: Int,
    emergencyPassesRemaining: Int,
    streakDays: Int,
    completedToday: Boolean,
    onSnooze: () -> Unit,
    onBack: () -> Unit,
    onStopAlarm: () -> Unit,
    onEmergencyStopAlarm: () -> Unit,
    onTechnicalStopAlarm: () -> Unit,
    isOnboardingPractice: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val occurrence = remember(occurrenceToken) { AlarmOccurrenceStore(context).find(occurrenceToken) }
    LaunchedEffect(occurrenceToken, isFiredAlarm) {
        if (isFiredAlarm && occurrence == null) onBack()
        else if (occurrence?.completedAt != null) viewModel.restoreCompleted(alarm, occurrence.token)
        else if (occurrence != null && occurrence.state != OccurrenceState.RINGING) onBack()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var alarmService by remember { mutableStateOf<AlarmForegroundService?>(null) }
    var emergencyStopOpen by rememberSaveable { mutableStateOf(false) }
    val currentService by rememberUpdatedState(alarmService)
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val ringingToken by AlarmForegroundService.ringingOccurrenceToken.collectAsStateWithLifecycle()
    LaunchedEffect(alarmService, foreground, ringingToken, occurrenceToken) {
        if (foreground && occurrenceToken != null && ringingToken == occurrenceToken) {
            alarmService?.dismissNotificationPopup(alarm.id, occurrenceToken)
        }
    }
    val currentState by rememberUpdatedState(uiState)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnRoutineComplete by rememberUpdatedState(onRoutineComplete)
    val currentOnCompletionPresentationFinished by rememberUpdatedState(onCompletionPresentationFinished)
    var completionDelivered by remember(occurrenceToken, alarm.id, isFiredAlarm) { mutableStateOf(false) }
    var completionDeadline by rememberSaveable(alarm.id, isFiredAlarm, occurrenceToken) { mutableStateOf<Long?>(null) }
    LaunchedEffect(alarmService, alarm.id) {
        alarmService?.finishedAlarmId?.collect { finishedId ->
            if (finishedId == alarm.id && currentState.phase != RoutinePhase.COMPLETE) currentOnBack()
        }
    }
    DisposableEffect(lifecycleOwner, alarm.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { foreground = true; viewModel.resume() }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    viewModel.pause()
                    currentService?.setRoutineLevel(alarm.id, 1f, occurrenceToken)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations == true) viewModel.pause() else viewModel.stop()
        }
    }
    DisposableEffect(alarm.id, isFiredAlarm, occurrenceToken) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                alarmService = (binder as? AlarmForegroundService.LocalBinder)?.service
            }
            override fun onServiceDisconnected(name: ComponentName?) { alarmService = null }
        }
        var bound = false
        if (isFiredAlarm && uiState.phase != RoutinePhase.COMPLETE && occurrence?.state == OccurrenceState.RINGING) {
            val serviceIntent = Intent(context, AlarmForegroundService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, occurrenceToken)
                .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes)
            ContextCompat.startForegroundService(context, serviceIntent)
            bound = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        }
        onDispose {
            if (currentState.phase != RoutinePhase.COMPLETE) alarmService?.setRoutineLevel(alarm.id, 1f, occurrenceToken)
            if (bound) context.unbindService(connection)
            alarmService = null
        }
    }
    DisposableEffect(activity, alarm.id, isFiredAlarm, uiState.phase == RoutinePhase.COMPLETE) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(completionDeadline, alarm.id, isFiredAlarm, uiState.phase) {
        if (uiState.phase != RoutinePhase.COMPLETE) {
            completionDeadline = null
            return@LaunchedEffect
        }
        val deadline = completionDeadline ?: return@LaunchedEffect
        delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isFiredAlarm) currentOnCompletionPresentationFinished()
    }
    var cameraStarted by rememberSaveable(alarm.id, isFiredAlarm, occurrenceToken) { mutableStateOf(false) }
    var lastCuedPoseIndex by rememberSaveable(alarm) { mutableStateOf(-1) }
    DisposableEffect(uiState.phase, uiState.poseIndex, cameraStarted, foreground) {
        val cueDue = uiState.phase == RoutinePhase.TRANSITION || uiState.phase == RoutinePhase.COMPLETE
        val cue = if (!isOnboardingPractice && cameraStarted && foreground && cueDue && lastCuedPoseIndex != uiState.poseIndex) {
            lastCuedPoseIndex = uiState.poseIndex
            runCatching {
                android.media.MediaPlayer.create(
                    context,
                    R.raw.pose_transition_ding,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    0,
                )
            }.getOrNull()
        } else null
        cue?.setVolume(0.6f, 0.6f)
        cue?.start()
        onDispose { cue?.release() }
    }
    val previewAudio = remember(alarm.id, alarm.sound) {
        AlarmAudio(context.applicationContext, alarm.sound, viewModel::onCameraError)
    }
    DisposableEffect(previewAudio, cameraStarted, foreground, uiState.phase == RoutinePhase.COMPLETE) {
        if (!isFiredAlarm && cameraStarted && foreground && alarm.soundEnabled && uiState.phase != RoutinePhase.COMPLETE) {
            previewAudio.start()
        }
        onDispose { previewAudio.close() }
    }
    LaunchedEffect(alarmService, previewAudio, cameraStarted, foreground, uiState.started) {
        if (cameraStarted && uiState.started && foreground) {
            var previousProgress = viewModel.progress.value.routine
            viewModel.progress.collect { value ->
                if (isFiredAlarm) {
                    alarmService?.setRoutineLevel(alarm.id, value.alarmLevel, occurrenceToken)
                    if (value.routine > previousProgress) alarmService?.recordActivity(alarm.id, occurrenceToken)
                } else previewAudio.setLevel(value.alarmLevel)
                previousProgress = value.routine
            }
        } else {
            alarmService?.setRoutineLevel(alarm.id, 1f, occurrenceToken)
        }
    }
    LaunchedEffect(uiState.phase, alarmService) {
        if (uiState.phase == RoutinePhase.COMPLETE) {
            previewAudio.close()
            alarmService?.finishRoutine(alarm.id, occurrenceToken, persistStop = false)
            if (completionDeadline == null) completionDeadline = SystemClock.elapsedRealtime() + 10_000L
            if (!completionDelivered) {
                completionDelivered = true
                currentOnRoutineComplete(occurrenceToken, occurrence?.completedAt ?: ZonedDateTime.now())
            }
        }
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var permissionRefresh by remember { mutableStateOf(0) }
    val hasPermission = remember(permissionRefresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var permissionRequestInFlight by rememberSaveable(alarm.id, isFiredAlarm, occurrenceToken) { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestInFlight = false
        permissionDenied = !granted
        permissionRefresh += 1
        if (granted) cameraStarted = true
    }
    fun startCamera() {
        alarmService?.recordActivity(alarm.id, occurrenceToken)
        if (hasPermission) {
            cameraStarted = true
        } else if (!permissionRequestInFlight) {
            permissionRequestInFlight = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(cameraStartRequested, foreground, alarmService) {
        // Wait for binding so notification Start also resets the inactivity deadline.
        if (cameraStartRequested && foreground && alarmService != null) {
            onCameraStartHandled()
            startCamera()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRefresh += 1 }
    LaunchedEffect(cameraStarted, hasPermission, alarm.id) {
        if (cameraStarted && hasPermission) {
            viewModel.start(alarm, occurrenceToken)
        }
    }
    BackHandler { if (!isFiredAlarm) onBack() }
    val showingRingingScreen = isFiredAlarm && uiState.phase != RoutinePhase.COMPLETE && !(cameraStarted && hasPermission)
    DisposableEffect(activity, showingRingingScreen) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val lightStatusBars = insets?.isAppearanceLightStatusBars
        val lightNavigationBars = insets?.isAppearanceLightNavigationBars
        insets?.isAppearanceLightStatusBars = showingRingingScreen
        insets?.isAppearanceLightNavigationBars = showingRingingScreen
        onDispose {
            lightStatusBars?.let { insets?.isAppearanceLightStatusBars = it }
            lightNavigationBars?.let { insets?.isAppearanceLightNavigationBars = it }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF07100A))
        .pointerInput(alarm.id, isFiredAlarm) {
            if (isFiredAlarm) awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                        currentService?.recordActivity(alarm.id, occurrenceToken)
                    }
                }
            }
        }
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        if (isOnboardingPractice && uiState.phase == RoutinePhase.COMPLETE) {
            OnboardingPracticeComplete(onContinue = onBack)
        } else if ((cameraStarted && hasPermission) || uiState.phase == RoutinePhase.COMPLETE) {
            RoutineCameraScreen(
                uiState = uiState,
                frames = viewModel.frames,
                progress = viewModel.progress,
                onPoseFrame = viewModel::onPoseFrame,
                onCameraError = viewModel::onCameraError,
                onCameraRetry = viewModel::onCameraRetry,
                showBack = !isFiredAlarm,
                showStop = isFiredAlarm && !alarm.stopButtonDisabled,
                showEmergencyStop = isFiredAlarm && alarm.stopButtonDisabled,
                showTechnicalStop = isFiredAlarm,
                completionStreakDays = streakDays,
                onBack = onBack,
                onStop = onStopAlarm,
                onEmergencyStop = { emergencyStopOpen = true },
                onTechnicalStop = onTechnicalStopAlarm,
            )
        } else {
            CameraReadyScreen(
                ringingAlarm = alarm.takeIf { isFiredAlarm },
                permissionDenied = permissionDenied,
                showBack = !isFiredAlarm,
                showStop = isFiredAlarm && !alarm.stopButtonDisabled,
                snoozeMinutes = alarm.snoozeMinutes,
                showSnooze = isFiredAlarm && remainingSnoozes > 0,
                showEmergencyStop = isFiredAlarm && alarm.stopButtonDisabled && !permissionDenied,
                showTechnicalStop = isFiredAlarm && permissionDenied,
                onSnooze = onSnooze,
                onStartCamera = ::startCamera,
                onBack = onBack,
                onStop = onStopAlarm,
                onEmergencyStop = { emergencyStopOpen = true },
                onTechnicalStop = onTechnicalStopAlarm,
            )
        }
    }

    if (emergencyStopOpen) {
        EmergencyStopDialog(
            streakDays = streakDays,
            completedToday = completedToday,
            passesRemaining = emergencyPassesRemaining,
            onDismiss = { emergencyStopOpen = false },
            onConfirm = {
                emergencyStopOpen = false
                onEmergencyStopAlarm()
            },
        )
    }
}

@Composable
private fun CameraReadyScreen(
    ringingAlarm: AlarmConfig?,
    permissionDenied: Boolean,
    showBack: Boolean,
    showStop: Boolean,
    showSnooze: Boolean,
    snoozeMinutes: Int,
    showEmergencyStop: Boolean,
    showTechnicalStop: Boolean,
    onSnooze: () -> Unit,
    onStartCamera: () -> Unit,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onEmergencyStop: () -> Unit,
    onTechnicalStop: () -> Unit,
) {
    if (ringingAlarm != null) {
        AlarmRingingScreen(
            alarm = ringingAlarm,
            permissionDenied = permissionDenied,
            showStop = showTechnicalStop || showStop,
            showSnooze = showSnooze,
            showEmergencyStop = showEmergencyStop,
            onStart = onStartCamera,
            onStop = if (showTechnicalStop) onTechnicalStop else onStop,
            onSnooze = onSnooze,
            onEmergencyStop = onEmergencyStop,
        )
        return
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101511))
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showBack) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.Start),
                    ) {
                        Text("‹ Back", color = Color.White)
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(top = 24.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                            "Place your phone before starting the camera."
                        },
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Button(
                onClick = onStartCamera,
                modifier = Modifier
                    .widthIn(max = 270.dp).fillMaxWidth()
                    .heightIn(min = 68.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC9EE73),
                    contentColor = Color(0xFF173421),
                ),
            ) {
                Text(
                    if (permissionDenied) "Allow camera" else "Start camera",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showSnooze) {
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier
                            .widthIn(max = 240.dp).fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Snooze $snoozeMinutes minutes", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                if (showTechnicalStop) {
                    TextButton(onClick = onTechnicalStop) {
                        Text("Stop alarm", color = Color.White.copy(alpha = 0.72f))
                    }
                } else if (showStop) {
                    TextButton(onClick = onStop) {
                        Text("Stop alarm", color = Color.White.copy(alpha = 0.72f))
                    }
                } else if (showEmergencyStop) {
                    TextButton(onClick = onEmergencyStop) {
                        Text("Emergency stop", color = Color.White.copy(alpha = 0.58f))
                    }
                }
                Text(
                    "Camera off · Processed on-device · Never recorded",
                    color = Color.White.copy(alpha = 0.52f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EmergencyStopDialog(
    streakDays: Int,
    completedToday: Boolean,
    passesRemaining: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val holdSeconds = if (passesRemaining > 0) 5 else 15
    val holdMillis = holdSeconds * 1_000L
    var holding by remember { mutableStateOf(false) }
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val holdProgress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(if (holding) holdSeconds * 1_000 else 150),
        label = "Emergency stop hold",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency stop") },
        text = {
            Column {
                Text(
                    if (completedToday) "You already completed yoga today. Stopping keeps your $streakDays-day streak."
                    else if (streakDays > 0) "Complete yoga before midnight to keep your $streakDays-day yoga streak."
                    else "Use this only when you cannot complete your yoga routine.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (passesRemaining > 0) "$passesRemaining of 3 emergency passes remaining this month."
                    else "No emergency passes remain this month.",
                    color = Color(0xFF647069),
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .width(220.dp)
                        .pointerInput(holdMillis) {
                            detectTapGestures(
                                onPress = {
                                    holding = true
                                    val heldLongEnough = try {
                                        withTimeoutOrNull(holdMillis) { tryAwaitRelease() } == null
                                    } finally {
                                        holding = false
                                    }
                                    if (heldLongEnough) currentOnConfirm()
                                },
                            )
                        }
                        .semantics {
                            onClick(label = "Stop alarm") {
                                currentOnConfirm()
                                true
                            }
                        },
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF8C2530),
                ) {
                    Text(
                        "Hold $holdSeconds seconds to stop",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.width(190.dp).padding(top = 6.dp),
                    color = Color(0xFF8C2530),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
    )
}
