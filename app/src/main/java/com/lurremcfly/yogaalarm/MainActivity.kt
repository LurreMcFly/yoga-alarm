package com.lurremcfly.yogaalarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import com.lurremcfly.yogaalarm.billing.PlayBillingManager
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.data.ProAccessStore
import com.lurremcfly.yogaalarm.alarm.AlarmForegroundService
import com.lurremcfly.yogaalarm.alarm.AlarmScheduler
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.ui.AlarmEditorScreen
import com.lurremcfly.yogaalarm.ui.AlarmHomeScreen
import com.lurremcfly.yogaalarm.ui.ProPaywallSheet
import com.lurremcfly.yogaalarm.ui.PrivacyInfoSheet
import com.lurremcfly.yogaalarm.ui.RoutineCameraScreen
import com.lurremcfly.yogaalarm.ui.RoutineUiState
import com.lurremcfly.yogaalarm.ui.RoutineViewModel
import com.lurremcfly.yogaalarm.ui.RoutinePhase
import com.lurremcfly.yogaalarm.ui.RoutineProgress
import com.lurremcfly.yogaalarm.model.PoseFrame
import com.lurremcfly.yogaalarm.audio.AlarmAudio
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val firedAlarmId = mutableStateOf<Long?>(null)
    private val firedAlarmRemainingSnoozes = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmForegroundService.ensureNotificationChannel(this)
        updateAlarmWindowState(intent)
        firedAlarmId.value = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L).takeIf { it >= 0L }
        firedAlarmRemainingSnoozes.value = intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, 0)
        enableEdgeToEdge()
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

@Composable
private fun YogaAlarmRoot(
    firedAlarmId: Long?,
    firedAlarmRemainingSnoozes: Int,
    onFiredAlarmHandled: () -> Unit,
    onAlarmPresentationFinished: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val routineViewModel: RoutineViewModel = viewModel()
    val store = remember { AlarmStore(context.applicationContext) }
    val proAccessStore = remember { ProAccessStore(context.applicationContext) }
    val billingManager = remember { PlayBillingManager(context.applicationContext) }
    val billingState by billingManager.state.collectAsStateWithLifecycle()
    var alarms by remember { mutableStateOf(store.load()) }
    var debugProPlan by remember { mutableStateOf(proAccessStore.load()) }
    val proPlan = debugProPlan ?: billingState.activePlan
    var proPaywallOpen by rememberSaveable { mutableStateOf(false) }
    var privacySheetOpen by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf("home") }
    var editingAlarmId by rememberSaveable { mutableStateOf(-1L) }
    var pendingEditorAlarm by rememberSaveable { mutableStateOf<AlarmConfig?>(null) }
    var cameraAlarm by rememberSaveable { mutableStateOf<AlarmConfig?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    fun saveAlarm(config: AlarmConfig) {
        val scheduled = AlarmScheduler.schedule(context, config)
        val saved = if (config.enabled && !scheduled) config.copy(enabled = false) else config
        alarms = if (alarms.any { it.id == saved.id }) {
            alarms.map { if (it.id == saved.id) saved else it }
        } else {
            alarms + saved
        }
        store.save(alarms)
        val message = when {
            config.enabled && !scheduled -> "Couldn’t schedule alarm. It’s saved and switched off. Check alarm permissions."
            saved.enabled -> AlarmScheduler.confirmation(saved)
            else -> "Alarm switched off"
        }
        snackbarHost.currentSnackbarData?.dismiss()
        snackbarScope.launch { snackbarHost.showSnackbar(message) }
    }
    var firedRoutine by rememberSaveable { mutableStateOf(false) }
    var remainingSnoozes by rememberSaveable { mutableStateOf(0) }
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

    DisposableEffect(billingManager) {
        billingManager.start()
        onDispose { billingManager.close() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { readinessRefresh += 1 }
    val cameraReady = remember(readinessRefresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val notificationsReady = remember(readinessRefresh) {
        val channelEnabled = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(AlarmForegroundService.CHANNEL_ID)
            ?.importance
            ?.let { it != NotificationManager.IMPORTANCE_NONE }
            ?: false
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() && channelEnabled
    }
    val fullScreenReady = remember(readinessRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val exactAlarmsReady = remember(readinessRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    LaunchedEffect(exactAlarmsReady) {
        if (exactAlarmsReady) {
            alarms.filter { it.enabled }.forEach { AlarmScheduler.schedule(context, it, preserveSnooze = true) }
        }
    }
    LaunchedEffect(firedAlarmId) {
        val alarmId = firedAlarmId ?: return@LaunchedEffect
        if (!firedRoutine || cameraAlarm?.id != alarmId) routineViewModel.stop()
        alarms = store.load()
        val fired = alarms.firstOrNull { it.id == alarmId } ?: return@LaunchedEffect
        proPaywallOpen = false
        privacySheetOpen = false
        editingAlarmId = fired.id
        pendingEditorAlarm = fired
        cameraAlarm = fired
        firedRoutine = true
        remainingSnoozes = if (fired.snoozeEnabled) {
            firedAlarmRemainingSnoozes.coerceIn(0, fired.snoozeCount)
        } else {
            0
        }
        route = "camera"
        onFiredAlarmHandled()
    }

    Box(Modifier.fillMaxSize()) {
        when (route) {
            "editor" -> {
                val initial = pendingEditorAlarm
                    ?: alarms.firstOrNull { it.id == editingAlarmId }
                    ?: AlarmConfig.create(editingAlarmId)
                AlarmEditorScreen(
                    initialAlarm = initial,
                    isPro = proPlan != null,
                    onUpgrade = { proPaywallOpen = true },
                    onCancel = {
                        pendingEditorAlarm = null
                        route = "home"
                    },
                    onSave = { saved ->
                        saveAlarm(saved)
                        pendingEditorAlarm = null
                        route = "home"
                    },
                    onTestRoutine = { draft ->
                        pendingEditorAlarm = draft
                        cameraAlarm = draft
                        firedRoutine = false
                        route = "camera"
                    },
                    onTestPose = { draft, step ->
                        pendingEditorAlarm = draft
                        cameraAlarm = draft.copy(routine = listOf(step))
                        firedRoutine = false
                        route = "camera"
                    },
                )
            }
            "camera" -> {
                val testAlarm = cameraAlarm ?: pendingEditorAlarm ?: AlarmConfig.create(editingAlarmId)
                val viewModel = routineViewModel
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                CameraPermissionRoute(
                    alarm = testAlarm,
                    uiState = uiState,
                    frames = viewModel.frames,
                    progress = viewModel.progress,
                    onPoseFrame = viewModel::onPoseFrame,
                    onCameraError = viewModel::onCameraError,
                    onCameraRetry = viewModel::onCameraRetry,
                    onStartRoutine = viewModel::start,
                    onPauseRoutine = viewModel::pause,
                    onResumeRoutine = viewModel::resume,
                    onStopRoutine = viewModel::stop,
                    onRoutineComplete = onAlarmPresentationFinished,
                    isFiredAlarm = firedRoutine,
                    remainingSnoozes = remainingSnoozes,
                    onSnooze = {
                        val scheduled = AlarmScheduler.scheduleSnooze(
                            context = context,
                            alarmId = testAlarm.id,
                            minutes = testAlarm.snoozeMinutes,
                            remainingSnoozes = (remainingSnoozes - 1).coerceAtLeast(0),
                        )
                        if (scheduled) {
                            context.stopService(Intent(context, AlarmForegroundService::class.java))
                            onAlarmPresentationFinished()
                            firedRoutine = false
                            remainingSnoozes = 0
                            pendingEditorAlarm = null
                            cameraAlarm = null
                            route = "home"
                        }
                    },
                    onBack = {
                        if (firedRoutine) {
                            context.stopService(Intent(context, AlarmForegroundService::class.java))
                            onAlarmPresentationFinished()
                            firedRoutine = false
                            remainingSnoozes = 0
                            pendingEditorAlarm = null
                            cameraAlarm = null
                            route = "home"
                        } else {
                            route = "editor"
                        }
                    },
                )
            }
            else -> AlarmHomeScreen(
                alarms = alarms,
                isPro = proPlan != null,
                onOpenPro = { proPaywallOpen = true },
                onOpenPrivacy = { privacySheetOpen = true },
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
                    val channelBlocked = context.getSystemService(NotificationManager::class.java)
                        .getNotificationChannel(AlarmForegroundService.CHANNEL_ID)
                        ?.importance == NotificationManager.IMPORTANCE_NONE
                    if (channelBlocked) {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmForegroundService.CHANNEL_ID),
                        )
                    } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requested) ||
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
                    alarms.firstOrNull { it.id == id }?.let { saveAlarm(it.copy(enabled = enabled)) }
                },
                onDeleteAlarm = { id ->
                    AlarmScheduler.cancel(context, id)
                    alarms = alarms.filterNot { it.id == id }
                    store.save(alarms)
                },
            )
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (proPaywallOpen) {
        ProPaywallSheet(
            activePlan = proPlan,
            prices = billingState.offers.mapValues { it.value.formattedPrice },
            billingReady = billingState.connected,
            purchaseInProgress = billingState.purchasing,
            billingMessage = billingState.message,
            onDismiss = { proPaywallOpen = false },
            onPurchase = { selectedPlan ->
                billingManager.purchase(context as Activity, selectedPlan)
            },
            onRestorePurchases = billingManager::restorePurchases,
            onManageSubscription = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS_URL)))
            },
            onActivateTestPlan = { selectedPlan ->
                proAccessStore.activateForTesting(selectedPlan)
                debugProPlan = selectedPlan
                proPaywallOpen = false
            },
        )
    }

    if (privacySheetOpen) {
        PrivacyInfoSheet(
            onDismiss = { privacySheetOpen = false },
            onReadPolicy = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            },
        )
    }
}

private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
private const val PRIVACY_POLICY_URL = "https://yoga-alarm.polite-eel-9991.chatgpt.site/privacy"

@Composable
private fun CameraPermissionRoute(
    alarm: AlarmConfig,
    uiState: RoutineUiState,
    frames: StateFlow<PoseFrame?>,
    progress: StateFlow<RoutineProgress>,
    onPoseFrame: (com.lurremcfly.yogaalarm.model.PoseFrame) -> Unit,
    onCameraError: (String) -> Unit,
    onCameraRetry: () -> Unit,
    onStartRoutine: (AlarmConfig) -> Unit,
    onPauseRoutine: () -> Unit,
    onResumeRoutine: () -> Unit,
    onStopRoutine: () -> Unit,
    onRoutineComplete: () -> Unit,
    isFiredAlarm: Boolean,
    remainingSnoozes: Int,
    onSnooze: () -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var alarmService by remember { mutableStateOf<AlarmForegroundService?>(null) }
    val currentService by rememberUpdatedState(alarmService)
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val currentState by rememberUpdatedState(uiState)
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(alarmService, alarm.id) {
        alarmService?.finishedAlarmId?.collect { finishedId ->
            if (finishedId == alarm.id && currentState.phase != RoutinePhase.COMPLETE) currentOnBack()
        }
    }
    DisposableEffect(lifecycleOwner, alarm.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { foreground = true; onResumeRoutine() }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    onPauseRoutine()
                    currentService?.setRoutineLevel(alarm.id, 1f)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations == true) onPauseRoutine() else onStopRoutine()
        }
    }
    DisposableEffect(alarm.id, isFiredAlarm) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                alarmService = (binder as? AlarmForegroundService.LocalBinder)?.service
            }
            override fun onServiceDisconnected(name: ComponentName?) { alarmService = null }
        }
        var bound = false
        if (isFiredAlarm && uiState.phase != RoutinePhase.COMPLETE) {
            val serviceIntent = Intent(context, AlarmForegroundService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes)
            ContextCompat.startForegroundService(context, serviceIntent)
            bound = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        }
        onDispose {
            if (currentState.phase != RoutinePhase.COMPLETE) alarmService?.setRoutineLevel(alarm.id, 1f)
            if (bound) context.unbindService(connection)
            alarmService = null
        }
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val lightStatusBars = insets?.isAppearanceLightStatusBars
        val lightNavigationBars = insets?.isAppearanceLightNavigationBars
        @Suppress("DEPRECATION")
        val navigationColor = window?.navigationBarColor
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        insets?.isAppearanceLightStatusBars = false
        insets?.isAppearanceLightNavigationBars = false
        @Suppress("DEPRECATION")
        window?.navigationBarColor = android.graphics.Color.rgb(7, 16, 10)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            lightStatusBars?.let { insets?.isAppearanceLightStatusBars = it }
            lightNavigationBars?.let { insets?.isAppearanceLightNavigationBars = it }
            @Suppress("DEPRECATION")
            navigationColor?.let { window.navigationBarColor = it }
        }
    }
    var cameraStarted by rememberSaveable(alarm.id, isFiredAlarm) { mutableStateOf(false) }
    var lastCuedPoseIndex by rememberSaveable(alarm) { mutableStateOf(-1) }
    DisposableEffect(uiState.phase, uiState.poseIndex, cameraStarted, foreground) {
        val cueDue = uiState.phase == RoutinePhase.TRANSITION || uiState.phase == RoutinePhase.COMPLETE
        val cue = if (cameraStarted && foreground && cueDue && lastCuedPoseIndex != uiState.poseIndex) {
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
        AlarmAudio(context.applicationContext, alarm.sound, onCameraError)
    }
    DisposableEffect(previewAudio, cameraStarted, foreground, uiState.phase == RoutinePhase.COMPLETE) {
        if (!isFiredAlarm && cameraStarted && foreground && alarm.soundEnabled && uiState.phase != RoutinePhase.COMPLETE) {
            previewAudio.start()
        }
        onDispose { previewAudio.close() }
    }
    LaunchedEffect(alarmService, previewAudio, cameraStarted, foreground, uiState.started) {
        if (cameraStarted && uiState.started && foreground) {
            progress.collect { value ->
                if (isFiredAlarm) alarmService?.setRoutineLevel(alarm.id, value.alarmLevel)
                else previewAudio.setLevel(value.alarmLevel)
            }
        } else {
            alarmService?.setRoutineLevel(alarm.id, 1f)
        }
    }
    LaunchedEffect(uiState.phase, alarmService) {
        if (uiState.phase == RoutinePhase.COMPLETE) {
            previewAudio.close()
            alarmService?.finishRoutine(alarm.id)
            onRoutineComplete()
        }
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var permissionRefresh by remember { mutableStateOf(0) }
    val hasPermission = remember(permissionRefresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        permissionRefresh += 1
        if (granted) cameraStarted = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRefresh += 1 }
    LaunchedEffect(cameraStarted, hasPermission, alarm.id) {
        if (cameraStarted && hasPermission) {
            onStartRoutine(alarm)
        }
    }
    BackHandler(enabled = isFiredAlarm) { }

    if (cameraStarted && hasPermission) {
        RoutineCameraScreen(
            uiState = uiState,
            frames = frames,
            progress = progress,
            onPoseFrame = onPoseFrame,
            onCameraError = onCameraError,
            onCameraRetry = onCameraRetry,
            showBack = !isFiredAlarm,
            onBack = onBack,
        )
    } else {
        CameraReadyScreen(
            permissionDenied = permissionDenied,
            showBack = !isFiredAlarm,
            snoozeMinutes = alarm.snoozeMinutes,
            showSnooze = isFiredAlarm && remainingSnoozes > 0,
            showEmergencyStop = isFiredAlarm && permissionDenied,
            onSnooze = onSnooze,
            onStartCamera = {
                if (hasPermission) {
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
    snoozeMinutes: Int,
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp, start = 20.dp, end = 20.dp),
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

        Button(
            onClick = onStartCamera,
            modifier = Modifier
                .align(Alignment.Center)
                .width(270.dp)
                .height(68.dp),
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
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showSnooze) {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier
                        .width(240.dp)
                        .height(54.dp),
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
            if (showEmergencyStop) {
                TextButton(onClick = onBack) {
                    Text("Stop alarm", color = Color.White.copy(alpha = 0.72f))
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
