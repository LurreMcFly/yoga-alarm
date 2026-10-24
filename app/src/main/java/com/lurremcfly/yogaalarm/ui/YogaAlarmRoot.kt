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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.lurremcfly.yogaalarm.model.YogaPose
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate

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
    var today by remember { mutableStateOf(LocalDate.now()) }
    var presentedStreakDays by rememberSaveable { mutableStateOf(wakeProgress.streakDays) }
    var savedAlarmId by rememberSaveable { mutableStateOf<Long?>(null) }
    val proEntitlement = billingState.entitlement
    var proPaywallOpen by rememberSaveable { mutableStateOf(false) }
    var privacySheetOpen by rememberSaveable { mutableStateOf(false) }
    var settingsSheetOpen by rememberSaveable { mutableStateOf(false) }
    val settingsPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var weekStartsOnSunday by remember {
        mutableStateOf(settingsPreferences.getBoolean("week_starts_on_sunday", false))
    }
    var route by rememberSaveable { mutableStateOf("home") }
    var editingAlarmId by rememberSaveable { mutableStateOf(-1L) }
    var pendingEditorAlarm by rememberSaveable { mutableStateOf<AlarmConfig?>(null) }
    var cameraAlarm by rememberSaveable { mutableStateOf<AlarmConfig?>(null) }
    val onboardingPreferences = remember { context.getSharedPreferences("onboarding", Context.MODE_PRIVATE) }
    var onboardingPending by rememberSaveable {
        mutableStateOf(!onboardingPreferences.getBoolean("completed_v1", false))
    }
    var onboardingInvitationOpen by rememberSaveable { mutableStateOf(false) }
    var onboardingStep by rememberSaveable {
        mutableStateOf<OnboardingStep?>(null)
    }
    val onboardingTargets = remember { OnboardingTargets() }
    var onboardingSavedAlarm by rememberSaveable { mutableStateOf<AlarmConfig?>(null) }
    var onboardingPractice by rememberSaveable { mutableStateOf(false) }
    var onboardingPracticeComplete by rememberSaveable { mutableStateOf(false) }
    var onboardingPracticePose by rememberSaveable { mutableStateOf<YogaPose?>(null) }
    fun finishOnboarding() {
        onboardingPreferences.edit().putBoolean("completed_v1", true)
            .putBoolean("permissions_introduced_v1", true).apply()
        onboardingPending = false
        onboardingInvitationOpen = false
        onboardingStep = null
        onboardingSavedAlarm = null
        if (route == "home") pendingEditorAlarm = null
    }
    val snackbarHost = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    fun saveAlarm(config: AlarmConfig): AlarmConfig {
        val scheduled = AlarmScheduler.schedule(context, config)
        val saved = if (config.enabled && !scheduled) config.copy(enabled = false) else config
        alarms = if (alarms.any { it.id == saved.id }) {
            alarms.map { if (it.id == saved.id) saved else it }
        } else {
            alarms + saved
        }
        store.save(alarms)
        wakeProgress = wakeProgressStore.load()
        val message = when {
            config.enabled && !scheduled -> "Couldn’t schedule alarm. It’s saved and switched off. Check alarm permissions."
            saved.enabled -> AlarmScheduler.confirmation(saved)
            else -> "Alarm switched off"
        }
        snackbarHost.currentSnackbarData?.dismiss()
        snackbarScope.launch { snackbarHost.showSnackbar(message) }
        return saved
    }
    var firedRoutine by rememberSaveable { mutableStateOf(false) }
    var remainingSnoozes by rememberSaveable { mutableStateOf(0) }
    fun closeFiredAlarm() {
        context.stopService(Intent(context, AlarmForegroundService::class.java))
        onAlarmPresentationFinished()
        firedRoutine = false
        remainingSnoozes = 0
        alarms = store.load()
        wakeProgress = wakeProgressStore.load()
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
    val fixCamera: () -> Unit = {
        val activity = context as? Activity
        val requested = permissionPreferences.getBoolean("camera_requested", false)
        if (requested && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        } else {
            permissionPreferences.edit().putBoolean("camera_requested", true).apply()
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val fixNotifications: () -> Unit = {
        val activity = context as? Activity
        val requested = permissionPreferences.getBoolean("notifications_requested", false)
        val channelBlocked = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(AlarmForegroundService.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        if (channelBlocked) {
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmForegroundService.CHANNEL_ID))
        } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requested) ||
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        ) {
            permissionPreferences.edit().putBoolean("notifications_requested", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    val fixFullScreen: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }
    val fixExactAlarms: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }

    DisposableEffect(billingManager) {
        billingManager.start()
        onDispose { billingManager.close() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        today = LocalDate.now()
        billingManager.start()
        readinessRefresh += 1
        alarms = store.load()
        wakeProgress = wakeProgressStore.load()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val currentDate = LocalDate.now()
            if (currentDate != today) {
                today = currentDate
                wakeProgress = wakeProgressStore.load()
            }
        }
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
    val alarmReady = cameraReady && notificationsReady && fullScreenReady && exactAlarmsReady
    val canOfferOnboarding = onboardingPending && route == "home" &&
        onboardingStep == null && firedAlarmId == null && !firedRoutine &&
        !settingsSheetOpen && !privacySheetOpen && !proPaywallOpen
    LaunchedEffect(canOfferOnboarding) {
        if (canOfferOnboarding) {
            delay(600)
            onboardingInvitationOpen = true
        } else {
            onboardingInvitationOpen = false
        }
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
        settingsSheetOpen = false
        onboardingPractice = false
        onboardingPracticeComplete = false
        onboardingPending = !onboardingPreferences.getBoolean("completed_v1", false)
        onboardingInvitationOpen = false
        onboardingStep = null
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

    val navigation = updateTransition(route, label = "Alarm screen")
    Box(Modifier.fillMaxSize()) {
        navigation.AnimatedContent(transitionSpec = {
            if (initialState == "home" && targetState == "editor") {
                (fadeIn(tween(1_100, delayMillis = 100)) + slideInVertically(tween(1_100, delayMillis = 100)) { it / 24 }) togetherWith
                    fadeOut(tween(450))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        }) { screen ->
            when (screen) {
                "editor" -> {
                    val initial = pendingEditorAlarm
                        ?: alarms.firstOrNull { it.id == editingAlarmId }
                        ?: AlarmConfig.create(editingAlarmId)
                    AlarmEditorScreen(
                        initialAlarm = initial,
                        existingAlarmNames = alarms.filter { it.id != initial.id }.map { it.name },
                        weekStartsOnSunday = weekStartsOnSunday,
                        isPro = proEntitlement != null,
                        onUpgrade = { proPaywallOpen = true },
                        onCancel = {
                            pendingEditorAlarm = null
                            route = "home"
                        },
                        onSave = { saved ->
                            val actual = saveAlarm(saved.copy(enabled = true))
                            savedAlarmId = saved.id
                            pendingEditorAlarm = null
                            route = "home"
                            if (onboardingStep == OnboardingStep.Save) {
                                onboardingSavedAlarm = actual
                                onboardingStep = OnboardingStep.Complete
                            }
                        },
                        onTestRoutine = { draft ->
                            pendingEditorAlarm = draft
                            cameraAlarm = draft
                            firedRoutine = false
                            onboardingPractice = false
                            route = "camera"
                        },
                        onTestPose = { draft, step ->
                            pendingEditorAlarm = draft
                            cameraAlarm = draft.copy(routine = listOf(step))
                            firedRoutine = false
                            onboardingPractice = false
                            route = "camera"
                        },
                        onboardingStep = onboardingStep.takeIf { firedAlarmId == null && !firedRoutine },
                        onboardingTargets = onboardingTargets,
                        onOnboardingStep = { step, draft ->
                            pendingEditorAlarm = draft
                            onboardingStep = step
                            if (step == OnboardingStep.Add) route = "home"
                        },
                        onSkipOnboarding = ::finishOnboarding,
                        onPractice = { draft ->
                            pendingEditorAlarm = draft
                            cameraAlarm = draft.copy(
                                routine = listOf(draft.routine.first().copy(durationSeconds = 5)),
                                soundEnabled = false, vibrationEnabled = false,
                            )
                            onboardingPractice = true
                            onboardingPracticeComplete = false
                            onboardingPracticePose = draft.routine.first().pose
                            firedRoutine = false
                            route = "camera"
                        },
                        practiceComplete = onboardingPracticeComplete && onboardingPracticePose == initial.routine.firstOrNull()?.pose,
                    )
                }
                "camera" -> {
                    val testAlarm = cameraAlarm ?: pendingEditorAlarm ?: AlarmConfig.create(editingAlarmId)
                    CameraPermissionRoute(
                        alarm = testAlarm,
                        viewModel = routineViewModel,
                        onRoutineComplete = {
                            if (onboardingPractice) onboardingPracticeComplete = true
                            else if (firedRoutine) wakeProgress = wakeProgressStore.recordCompletion()
                        },
                        onCompletionPresentationFinished = onAlarmPresentationFinished,
                        isFiredAlarm = firedRoutine,
                        isOnboardingPractice = onboardingPractice,
                        remainingSnoozes = remainingSnoozes,
                        emergencyPassesRemaining = wakeProgress.emergencyPassesRemaining,
                        streakDays = wakeProgress.streakDays,
                        completedToday = wakeProgress.lastCompletionDate == java.time.LocalDate.now(),
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
                                if (onboardingPractice) {
                                    val complete = routineViewModel.uiState.value.phase == RoutinePhase.COMPLETE
                                    onboardingPracticeComplete = complete
                                    onboardingStep = if (complete) OnboardingStep.Save else OnboardingStep.Placement
                                    onboardingPractice = false
                                }
                                cameraAlarm = null
                                readinessRefresh += 1
                                route = if (pendingEditorAlarm == null) "home" else "editor"
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
                    bestStreakDays = wakeProgress.bestStreakDays,
                    today = today,
                    completedDates = wakeProgress.completedDates,
                    pausedDates = wakeProgress.pausedDates(today),
                    scheduledDates = wakeProgress.scheduledDates,
                    weekStartsOnSunday = weekStartsOnSunday,
                    onStreakPresented = { presentedStreakDays = wakeProgress.streakDays },
                    savedAlarmId = savedAlarmId,
                    onSavedAlarmPresented = { if (savedAlarmId == it) savedAlarmId = null },
                    isPro = proEntitlement != null,
                    onOpenPro = { if (onboardingStep == null) proPaywallOpen = true },
                    onOpenSettings = { if (onboardingStep == null) settingsSheetOpen = true },
                    cameraReady = cameraReady,
                    notificationsReady = notificationsReady,
                    fullScreenReady = fullScreenReady,
                    exactAlarmsReady = exactAlarmsReady,
                    onFixCamera = fixCamera,
                    onFixNotifications = fixNotifications,
                    onFixFullScreen = fixFullScreen,
                    onFixExactAlarms = fixExactAlarms,
                    onAddAlarm = {
                        val draft = pendingEditorAlarm.takeIf { onboardingStep == OnboardingStep.Add }
                            ?: AlarmConfig.create(existingNames = alarms.map { it.name })
                        editingAlarmId = draft.id
                        pendingEditorAlarm = draft
                        if (onboardingStep == OnboardingStep.Add) onboardingStep = OnboardingStep.Time
                        route = "editor"
                    },
                    onEditAlarm = {
                        if (onboardingStep == null) {
                            editingAlarmId = it
                            pendingEditorAlarm = null
                            route = "editor"
                        }
                    },
                    onToggleAlarm = { id, enabled ->
                        if (onboardingStep == null) alarms.firstOrNull { it.id == id }?.let { saveAlarm(it.copy(enabled = enabled)) }
                    },
                    onDeleteAlarm = { id ->
                        if (onboardingStep == null) {
                            AlarmScheduler.cancel(context, id)
                            alarms = alarms.filterNot { it.id == id }
                            store.save(alarms)
                            wakeProgress = wakeProgressStore.load()
                        }
                    },
                    onReorderAlarms = { reordered ->
                        if (onboardingStep == null) {
                            alarms = reordered
                            store.save(alarms)
                        }
                    },
                    reorderingEnabled = onboardingStep == null,
                    onboardingTargets = onboardingTargets,
                )
            }
        }
        if (route == "home" && firedAlarmId == null && !firedRoutine) {
            onboardingStep?.let { step ->
                OnboardingOverlay(step, onboardingTargets, onboardingSavedAlarm ?: pendingEditorAlarm,
                    alarmReady = alarmReady,
                    onStep = {
                        if (step == OnboardingStep.Permissions) {
                            onboardingPreferences.edit().putBoolean("permissions_introduced_v1", true).apply()
                        }
                        onboardingStep = it
                    }, onSkip = ::finishOnboarding, onPractice = {},
                    permissionContent = {
                        AlarmReadinessCard(cameraReady, notificationsReady, fullScreenReady, exactAlarmsReady,
                            fixCamera, fixNotifications, fixFullScreen, fixExactAlarms)
                    },
                    onTest = {
                        val saved = onboardingSavedAlarm
                        if (saved != null) {
                            finishOnboarding()
                            pendingEditorAlarm = null
                            cameraAlarm = saved
                            firedRoutine = false
                            route = "camera"
                        }
                    },
                )
            }
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        if (navigation.isRunning && route == "editor" && navigation.currentState == "home") {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
        }
    }

    if (onboardingInvitationOpen && canOfferOnboarding) {
        OnboardingInvitation(
            cardBounds = onboardingTargets.readinessBounds,
            onStart = {
                onboardingPending = false
                onboardingInvitationOpen = false
                onboardingStep = if (!alarmReady && !onboardingPreferences.getBoolean("permissions_introduced_v1", false)) {
                    OnboardingStep.Permissions
                } else OnboardingStep.Welcome
            },
            onLater = ::finishOnboarding,
        )
    }

    if (proPaywallOpen) {
        ProPaywallSheet(
            entitlement = proEntitlement,
            subscriptionAutoRenewing = billingState.subscriptionAutoRenewing,
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

    if (settingsSheetOpen) {
        AppSettingsSheet(
            entitlement = proEntitlement,
            subscriptionAutoRenewing = billingState.subscriptionAutoRenewing,
            weekStartsOnSunday = weekStartsOnSunday,
            onWeekStartsOnSundayChange = {
                weekStartsOnSunday = it
                settingsPreferences.edit().putBoolean("week_starts_on_sunday", it).apply()
            },
            onDismiss = { settingsSheetOpen = false },
            onOpenPro = {
                settingsSheetOpen = false
                proPaywallOpen = true
            },
            onManageSubscription = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS_URL)))
            },
            onOpenPrivacy = {
                settingsSheetOpen = false
                privacySheetOpen = true
            },
            onOpenHelp = {
                settingsSheetOpen = false
                onboardingPending = false
                onboardingInvitationOpen = false
                pendingEditorAlarm = null
                onboardingSavedAlarm = null
                onboardingPracticeComplete = false
                onboardingStep = OnboardingStep.Welcome
                route = "home"
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

private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions?sku=yoga_alarm_pro&package=com.lurremcfly.yogaalarm"
private const val PRIVACY_POLICY_URL = "https://www.yogaalarm.com/privacy"
