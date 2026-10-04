package com.lurremcfly.yogaalarm.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lurremcfly.yogaalarm.alarm.AlarmForegroundService
import com.lurremcfly.yogaalarm.alarm.AlarmScheduler
import com.lurremcfly.yogaalarm.billing.PlayBillingManager
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.data.WakeProgressStore
import com.lurremcfly.yogaalarm.model.AlarmConfig
import kotlinx.coroutines.launch

@Composable
internal fun YogaAlarmRoot(
    firedAlarmId: Long?,
    firedAlarmRemainingSnoozes: Int,
    onFiredAlarmHandled: () -> Unit,
    onAlarmPresentationFinished: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val routineViewModel: RoutineViewModel = viewModel()
    val store = remember { AlarmStore(context.applicationContext) }
    val wakeProgressStore = remember { WakeProgressStore(context.applicationContext) }
    val billingManager = remember { PlayBillingManager(context.applicationContext) }
    val billingState by billingManager.state.collectAsStateWithLifecycle()
    var alarms by remember { mutableStateOf(store.load()) }
    var wakeProgress by remember { mutableStateOf(wakeProgressStore.load()) }
    var presentedStreakDays by rememberSaveable { mutableStateOf(wakeProgress.streakDays) }
    var savedAlarmId by rememberSaveable { mutableStateOf<Long?>(null) }
    val proEntitlement = billingState.entitlement
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
    fun closeFiredAlarm() {
        context.stopService(Intent(context, AlarmForegroundService::class.java))
        onAlarmPresentationFinished()
        firedRoutine = false
        remainingSnoozes = 0
        pendingEditorAlarm = null
        cameraAlarm = null
        route = "home"
    }
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        billingManager.start()
        readinessRefresh += 1
        wakeProgress = wakeProgressStore.load()
    }
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
                    isPro = proEntitlement != null,
                    onUpgrade = { proPaywallOpen = true },
                    onCancel = {
                        pendingEditorAlarm = null
                        route = "home"
                    },
                    onSave = { saved ->
                        saveAlarm(saved)
                        savedAlarmId = saved.id
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
                CameraPermissionRoute(
                    alarm = testAlarm,
                    viewModel = routineViewModel,
                    onRoutineComplete = {
                        if (firedRoutine) wakeProgress = wakeProgressStore.recordCompletion()
                    },
                    onCompletionPresentationFinished = onAlarmPresentationFinished,
                    isFiredAlarm = firedRoutine,
                    remainingSnoozes = remainingSnoozes,
                    emergencyPassesRemaining = wakeProgress.emergencyPassesRemaining,
                    streakDays = wakeProgress.streakDays,
                    onSnooze = {
                        val scheduled = AlarmScheduler.scheduleSnooze(
                            context = context,
                            alarmId = testAlarm.id,
                            minutes = testAlarm.snoozeMinutes,
                            remainingSnoozes = (remainingSnoozes - 1).coerceAtLeast(0),
                        )
                        if (scheduled) {
                            closeFiredAlarm()
                        }
                    },
                    onBack = {
                        if (firedRoutine) {
                            closeFiredAlarm()
                        } else {
                            route = "editor"
                        }
                    },
                    onStopAlarm = {
                        wakeProgress = wakeProgressStore.recordStop(consumeEmergencyPass = false)
                        closeFiredAlarm()
                    },
                    onEmergencyStopAlarm = {
                        wakeProgress = wakeProgressStore.recordStop(consumeEmergencyPass = true)
                        closeFiredAlarm()
                    },
                    onTechnicalStopAlarm = { closeFiredAlarm() },
                )
            }
            else -> AlarmHomeScreen(
                alarms = alarms,
                streakDays = wakeProgress.streakDays,
                previousStreakDays = presentedStreakDays,
                onStreakPresented = { presentedStreakDays = wakeProgress.streakDays },
                savedAlarmId = savedAlarmId,
                onSavedAlarmPresented = { if (savedAlarmId == it) savedAlarmId = null },
                isPro = proEntitlement != null,
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
            entitlement = proEntitlement,
            prices = billingState.offers.mapValues { it.value.formattedPrice },
            billingReady = billingState.connected,
            loadingPrices = billingState.loadingOffers,
            purchaseInProgress = billingState.purchasing,
            billingMessage = billingState.message,
            onDismiss = { proPaywallOpen = false },
            onPurchase = { selectedPlan ->
                billingManager.purchase(context as Activity, selectedPlan)
            },
            onRestorePurchases = billingManager::restorePurchases,
            onRetry = billingManager::start,
            onManageSubscription = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS_URL)))
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
private const val PRIVACY_POLICY_URL = "https://www.yogaalarm.com/privacy"
