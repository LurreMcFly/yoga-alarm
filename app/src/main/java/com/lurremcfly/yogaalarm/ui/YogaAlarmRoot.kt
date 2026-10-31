package com.lurremcfly.yogaalarm.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore
import com.lurremcfly.yogaalarm.data.ProTrialStore
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.analytics.MonetizationAnalytics
import com.lurremcfly.yogaalarm.data.WakeProgressStore
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmOccurrence
import com.lurremcfly.yogaalarm.model.YogaPose
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun YogaAlarmRoot(
    firedAlarmId: Long?,
    firedOccurrenceToken: String?,
    cameraStartAlarmId: Long?,
    onCameraStartHandled: (Long) -> Unit,
    onFiredAlarmHandled: () -> Unit,
    onAlarmPresentationFinished: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val routineViewModel: RoutineViewModel = viewModel()
    val trialStore = remember { ProTrialStore(context.applicationContext) }
    var trial by remember { mutableStateOf(trialStore.enroll(BuildConfig.PRO_TRIAL_ROLLOUT_ENABLED)) }
    val analytics = remember { MonetizationAnalytics(context.applicationContext) }
    val occurrenceStore = remember { AlarmOccurrenceStore(context.applicationContext) }
    val store = remember { AlarmStore(context.applicationContext) }
    val wakeProgressStore = remember { WakeProgressStore(context.applicationContext) }
    fun recordCompletionProgress(occurrence: AlarmOccurrence) {
        if (occurrence.progressRecorded) return
        val completedAt = occurrence.completedAt ?: return
        runCatching {
            wakeProgressStore.recordCompletion(completedAt)
            occurrenceStore.markProgressRecorded(occurrence.token)
        }
    }
    val billingManager = remember { PlayBillingManager(context.applicationContext) }
    val billingState by billingManager.state.collectAsStateWithLifecycle()
    var alarms by remember { mutableStateOf(store.load()) }
    var wakeProgress by remember { mutableStateOf(wakeProgressStore.load()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var presentedStreakDays by rememberSaveable { mutableStateOf(wakeProgress.streakDays) }
    var savedAlarmId by rememberSaveable { mutableStateOf<Long?>(null) }
    var internalNonPaying by remember { mutableStateOf(trialStore.internalNonPayingEnabled()) }
    val proEntitlement = billingState.entitlement.takeUnless { internalNonPaying }
    // Preview presentation can use real prices for an owner, but must never enable another purchase.
    val trialOfferAvailable = (if (internalNonPaying) billingState.copy(entitlement = null).canPurchase
        else billingState.canPurchase) && billingState.offers.isNotEmpty()
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
    var editorBase by rememberSaveable { mutableStateOf<AlarmConfig?>(null) }
    var cameraAlarm by rememberSaveable { mutableStateOf<AlarmConfig?>(null) }
    var routineOccurrenceToken by rememberSaveable { mutableStateOf<String?>(null) }
    var freeSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var restoreOpen by rememberSaveable { mutableStateOf(false) }
    var restorationOffered by rememberSaveable { mutableStateOf(settingsPreferences.getBoolean("trial_restoration_handled", false)) }
    fun finishRestoration() {
        restoreOpen = false
        restorationOffered = true
        settingsPreferences.edit().putBoolean("trial_restoration_handled", true).apply()
    }
    var restoreSelected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var paywallEntry by rememberSaveable { mutableStateOf("manual") }
    var paywallFeature by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleMilestone by rememberSaveable { mutableStateOf<Int?>(null) }
    var internalExpiryBanner by remember { mutableStateOf(trialStore.internalExpiryBannerEnabled()) }
    var testingBusy by remember { mutableStateOf(false) }
    var testingMessage by remember { mutableStateOf<String?>(null) }
    var resumed by remember { mutableStateOf(false) }
    val effectivePro = proEntitlement != null || trial.active
    val hasUnreviewedAlarms = !effectivePro && alarms.any(trial::needsFreeReview)
    val trialStatus = (if (proEntitlement != null) null else when {
        trial.active -> "Pro preview · ${trial.remaining} alarm days left"
        hasUnreviewedAlarms -> "Pro preview complete. Review free settings for alarms that still use a Pro routine or sound."
        trial.awaitingPurchaseResolution -> "Pro preview complete. Checking purchase status."
        trial.exhausted -> "Your Pro preview is complete. Future alarms use your free settings."
        else -> null
    })?.let { if (internalNonPaying) "TEST MODE · $it" else it }
        ?: if (internalNonPaying) "TEST MODE · Non-paying access. Start a preview in Settings." else null
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
    fun saveAlarm(config: AlarmConfig, base: AlarmConfig? = editorBase, enableOnSave: Boolean = false): AlarmConfig? {
        val savedDraft = store.saveEdited(config, base, billingManager.state.value.entitlement != null, enableOnSave)
        if (savedDraft == null) {
            alarms = store.load()
            snackbarScope.launch { snackbarHost.showSnackbar("Settings changed. Review your routine and free settings before saving.") }
            return null
        }
        val repaired = !effectivePro && base != null && trial.needsFreeReview(base) && savedDraft.customization.isFree
        val scheduled = AlarmScheduler.schedule(context, savedDraft, preserveSnooze = repaired)
        val saved = if (savedDraft.enabled && !scheduled) savedDraft.copy(enabled = false) else savedDraft
        alarms = if (saved != savedDraft) store.update { list -> list.map { if (it.id == saved.id) it.copy(enabled = false) else it } } else store.load()
        wakeProgress = wakeProgressStore.load()
        if (base == null) analytics.recordOnce("first_alarm_saved", "installation")
        if (trial.active && !saved.customization.isFree) {
            analytics.record("trial_customization_saved", mapOf(
                "pose_count" to saved.routine.size.toString(),
                "hold_customized" to saved.routine.any { it.durationSeconds != 20 }.toString(),
                "sound_customized" to (!saved.sound.isFree).toString(),
            ))
            if (base?.trialFallback != saved.trialFallback) analytics.record("fallback_confirmed")
        }
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
    fun closeFiredAlarm(token: String?, preserveSnooze: Boolean = false) {
        if (token != routineOccurrenceToken || (firedOccurrenceToken != null && token != firedOccurrenceToken && firedAlarmId != null)) return
        context.startService(Intent(context, AlarmForegroundService::class.java)
            .setAction(AlarmForegroundService.ACTION_STOP_ALARM)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, cameraAlarm?.id ?: -1L)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, token)
            .putExtra(AlarmForegroundService.EXTRA_PRESERVE_OCCURRENCE, preserveSnooze))
        onAlarmPresentationFinished()
        firedRoutine = false
        remainingSnoozes = 0
        alarms = store.load()
        wakeProgress = wakeProgressStore.load()
        pendingEditorAlarm = null
        cameraAlarm = null
        routineOccurrenceToken = null
        route = "home"
    }
    var readinessRefresh by remember { mutableStateOf(0) }
    var permissionFlowInFlight by rememberSaveable { mutableStateOf(false) }
    val permissionPreferences = remember {
        context.getSharedPreferences("alarm_readiness", android.content.Context.MODE_PRIVATE)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionFlowInFlight = false; readinessRefresh += 1 }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionFlowInFlight = false; readinessRefresh += 1 }
    val fixCamera: () -> Unit = {
        val activity = context as? Activity
        val requested = permissionPreferences.getBoolean("camera_requested", false)
        if (requested && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        } else {
            permissionPreferences.edit().putBoolean("camera_requested", true).apply()
            permissionFlowInFlight = true
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
            permissionFlowInFlight = true
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
        resumed = true
        today = LocalDate.now()
        billingManager.start()
        readinessRefresh += 1
        alarms = store.load()
        wakeProgress = wakeProgressStore.load()
        trial = trialStore.load()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            analytics.setAssignment(if (trial.enrollment == com.lurremcfly.yogaalarm.model.TrialEnrollment.PREVIEW) "trial" else trial.enrollment.name.lowercase(), 1, trial.enrolledAt)
            analytics.recordOnce("first_launch", "installation")
            analytics.recordOnce("experiment_assigned", "installation")
            occurrenceStore.pendingCompletions().sortedBy { it.completedAt }.forEach(::recordCompletionProgress)
        }
        wakeProgress = wakeProgressStore.load()
    }
    LaunchedEffect(billingState.entitlement, billingState.verifyingPurchases, billingState.purchasing, billingState.pendingPurchase, billingState.checkoutCanceled, billingState.ownershipResolution, billingState.recoverablePaidAccess, trial.transition, internalNonPaying, hasUnreviewedAlarms) {
        trial = withContext(Dispatchers.IO) {
            val state = billingManager.state.value
            trialStore.reconcile(state.entitlement != null, state.verifyingPurchases || state.purchasing, state.recoverablePaidAccess,
                state.ownershipResolution == com.lurremcfly.yogaalarm.billing.OwnershipResolution.RESOLVED || state.pendingPurchase || state.checkoutCanceled)
        }
        alarms = store.load()
        if (trialStore.hasPaidAccess(billingManager.state.value.entitlement != null)) {
            proPaywallOpen = false
            visibleMilestone = null
            restoreSelected = alarms.filter { it.trialOriginal != null && it.customization == it.trialFallback }.map { it.id }.toSet()
            if (!restorationOffered && restoreSelected.isNotEmpty()) restoreOpen = true
        }
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
    LaunchedEffect(firedAlarmId, firedOccurrenceToken) {
        val alarmId = firedAlarmId ?: return@LaunchedEffect
        val occurrence = occurrenceStore.find(firedOccurrenceToken) ?: return@LaunchedEffect
        if (occurrence.alarm.id != alarmId) return@LaunchedEffect
        if (occurrence.terminal) { onFiredAlarmHandled(); return@LaunchedEffect }
        if (!firedRoutine || routineOccurrenceToken != occurrence.token) routineViewModel.stop()
        alarms = store.load()
        val fired = occurrence.alarm
        routineOccurrenceToken = occurrence.token
        proPaywallOpen = false
        privacySheetOpen = false
        settingsSheetOpen = false
        freeSettingsOpen = false
        restoreOpen = false
        visibleMilestone = null
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
            occurrence.remainingSnoozes.coerceIn(0, fired.snoozeCount)
        } else {
            0
        }
        route = "camera"
        onFiredAlarmHandled()
    }

    val safeHome = resumed && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
        route == "home" && !firedRoutine && firedAlarmId == null &&
        AlarmForegroundService.ringingAlarm.value == null && !occurrenceStore.hasInFlight() && !onboardingPending && onboardingStep == null &&
        !onboardingInvitationOpen && !permissionFlowInFlight && !settingsSheetOpen && !privacySheetOpen && !freeSettingsOpen && !restoreOpen
    fun testingBlocked(): Boolean {
        val billing = billingManager.state.value
        return !BuildConfig.PRO_TRIAL_INTERNAL_TESTING || !resumed || route != "home" ||
            firedRoutine || firedAlarmId != null || AlarmForegroundService.ringingAlarm.value != null ||
            occurrenceStore.hasInFlight() || onboardingPending || onboardingStep != null ||
            onboardingInvitationOpen || permissionFlowInFlight || proPaywallOpen || restoreOpen ||
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            billing.purchasing || billing.verifyingPurchases || billing.pendingPurchase ||
            billing.ownershipResolution == com.lurremcfly.yogaalarm.billing.OwnershipResolution.LOADING
    }
    fun changeInternalTestState(reset: Boolean = false, change: () -> Boolean) {
        if (testingBusy || testingBlocked()) return
        testingBusy = true
        snackbarScope.launch {
            try {
                val changed = withContext(Dispatchers.IO) { change() }
                trial = trialStore.load()
                alarms = store.load()
                internalExpiryBanner = trialStore.internalExpiryBannerEnabled()
                internalNonPaying = trialStore.internalNonPayingEnabled()
                visibleMilestone = null
                testingMessage = if (changed) "Saved. Close Settings to see the Home presentation."
                    else "No change saved. An alarm or billing operation may be active; retry when idle."
                if (changed && reset) {
                    val cleared = withContext(Dispatchers.IO) {
                        settingsPreferences.edit().remove("trial_restoration_handled").commit()
                    }
                    if (cleared) {
                        restorationOffered = false
                        restoreSelected = emptySet()
                    } else testingMessage = "Preview reset, but the restoration-offer marker could not be saved. Retry reset before testing a purchase."
                }
            } finally {
                testingBusy = false
            }
        }
    }
    LaunchedEffect(alarms, editingAlarmId) {
        val current = alarms.firstOrNull { it.id == editingAlarmId }
        val base = editorBase
        if (current != null && base != null && current.customization != base.customization) {
            editorBase = base.copy(routine = current.routine, sound = current.sound,
                trialFallback = current.trialFallback, trialCustomization = current.trialCustomization, trialOriginal = current.trialOriginal)
            pendingEditorAlarm = pendingEditorAlarm?.copy(routine = current.routine, sound = current.sound,
                trialFallback = current.trialFallback, trialCustomization = current.trialCustomization, trialOriginal = current.trialOriginal)
        }
    }
    LaunchedEffect(safeHome, trial.pendingMilestone, trialOfferAvailable, proPaywallOpen, internalExpiryBanner, internalNonPaying) {
        val milestone = trial.pendingMilestone ?: return@LaunchedEffect
        if (!safeHome || proPaywallOpen || trialStore.hasPaidAccess(billingManager.state.value.entitlement != null)) return@LaunchedEffect
        if (milestone == 4) visibleMilestone = milestone
        else if (BuildConfig.PRO_TRIAL_ROLLOUT_ENABLED && trialOfferAvailable) {
            if (milestone == 2 || (milestone == 5 && BuildConfig.PRO_TRIAL_INTERNAL_TESTING && internalExpiryBanner)) visibleMilestone = milestone
            else {
                paywallEntry = "expiry"
                proPaywallOpen = true
            }
        }
    }
    val paywallVisible = proPaywallOpen && resumed && !firedRoutine && firedAlarmId == null &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    var paywallMeasured by remember(proPaywallOpen) { mutableStateOf(false) }
    LaunchedEffect(paywallVisible, trialOfferAvailable) {
        if (paywallVisible && proEntitlement == null) {
            if (trialOfferAvailable) {
                if (!paywallMeasured) {
                    analytics.record("paywall_viewed", mapOf("entry_point" to paywallEntry))
                    paywallMeasured = true
                }
                if (paywallEntry == "expiry" && trial.pendingMilestone == 5 && safeHome) {
                    withContext(Dispatchers.IO) { trialStore.markPresented(5) }
                    trial = trialStore.load()
                    analytics.record("trial_milestone_presented", mapOf("milestone" to "5"))
                }
            } else analytics.record("offer_unavailable", mapOf("entry_point" to paywallEntry))
        }
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
                    val current = alarms.firstOrNull { it.id == editingAlarmId }
                    val initial = pendingEditorAlarm?.let { draft ->
                        if (current != null && current.customization != editorBase?.customization) draft.copy(
                            routine = current.routine, sound = current.sound, trialFallback = current.trialFallback,
                            trialCustomization = current.trialCustomization, trialOriginal = current.trialOriginal,
                        ) else draft
                    } ?: current
                        ?: AlarmConfig.create(editingAlarmId)
                    AlarmEditorScreen(
                        initialAlarm = initial,
                        existingAlarmNames = alarms.filter { it.id != initial.id }.map { it.name },
                        weekStartsOnSunday = weekStartsOnSunday,
                        isPro = effectivePro,
                        trialActive = trial.active && proEntitlement == null,
                        needsFreeReview = !effectivePro && current != null && trial.needsFreeReview(current),
                        onUpgrade = { feature ->
                            paywallEntry = "locked_feature"
                            paywallFeature = when (feature) {
                                "pose" -> "Pro unlocks all nine poses, including this pose."
                                "duration" -> "Pro lets you choose holds of 5, 10, 15, 20, 30, 45 or 60 seconds."
                                "count" -> "Pro lets you choose 1–10 poses for your routine."
                                "sound" -> "Pro unlocks all ten alarm sounds."
                                else -> null
                            }
                            proPaywallOpen = true
                        },
                        onCancel = {
                            pendingEditorAlarm = null
                            route = "home"
                        },
                        onSave = { saved ->
                            val actual = saveAlarm(saved.copy(enabled = true), enableOnSave = true) ?: return@AlarmEditorScreen
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
                    val routeToken = routineOccurrenceToken
                    CameraPermissionRoute(
                        alarm = testAlarm,
                        occurrenceToken = routeToken,
                        viewModel = routineViewModel,
                        onRoutineComplete = { token, completedAt ->
                            if (onboardingPractice) onboardingPracticeComplete = true
                            else if (firedRoutine && token != null && token == routineOccurrenceToken) {
                                snackbarScope.launch {
                                    val previousTrial = trialStore.load()
                                    val committed = withContext(Dispatchers.IO) {
                                        trialStore.complete(token, completedAt,
                                            paid = { billingManager.state.value.entitlement != null },
                                            verifying = { billingManager.state.value.verifyingPurchases || billingManager.state.value.purchasing })
                                    }
                                    if (committed) {
                                        withContext(Dispatchers.IO) {
                                            occurrenceStore.find(token)?.let(::recordCompletionProgress)
                                        }
                                        wakeProgress = wakeProgressStore.load()
                                        trial = trialStore.load()
                                        alarms = store.load()
                                        analytics.recordOnce("first_real_alarm_completed", "installation")
                                        analytics.recordOnce("alarm_day_completed", completedAt.toLocalDate().toString())
                                        if (trial.transition != previousTrial.transition && trial.transition == com.lurremcfly.yogaalarm.model.FreeTransition.APPLIED) analytics.recordOnce("free_transition_applied", "preview_v1")
                                    }
                                }
                            }
                        },
                        onCompletionPresentationFinished = { if (routeToken == routineOccurrenceToken && firedAlarmId == null) onAlarmPresentationFinished() },
                        isFiredAlarm = firedRoutine,
                        cameraStartRequested = firedRoutine && cameraStartAlarmId == testAlarm.id && routeToken == firedOccurrenceToken,
                        onCameraStartHandled = { onCameraStartHandled(testAlarm.id) },
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
                                occurrenceToken = routeToken,
                            )
                            if (scheduled) {
                                closeFiredAlarm(routeToken, preserveSnooze = true)
                            }
                        },
                        onBack = {
                            if (firedRoutine) {
                                closeFiredAlarm(routeToken)
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
                            if (routeToken == routineOccurrenceToken) {
                                wakeProgress = wakeProgressStore.recordStop(consumeEmergencyPass = false)
                                closeFiredAlarm(routeToken)
                            }
                        },
                        onEmergencyStopAlarm = {
                            if (routeToken == routineOccurrenceToken) {
                                wakeProgress = wakeProgressStore.recordStop(consumeEmergencyPass = true)
                                closeFiredAlarm(routeToken)
                            }
                        },
                        onTechnicalStopAlarm = { closeFiredAlarm(routeToken) },
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
                    trialStatus = trialStatus,
                    trialMilestone = if (safeHome) visibleMilestone ?: trial.pendingMilestone else null,
                    internalExpiryBanner = BuildConfig.PRO_TRIAL_INTERNAL_TESTING && internalExpiryBanner && trial.exhausted,
                    offerAvailable = trialOfferAvailable,
                    onTrialMilestonePresented = { milestone ->
                        if (trial.pendingMilestone == milestone) snackbarScope.launch {
                            withContext(Dispatchers.IO) { trialStore.markPresented(milestone) }
                            trial = trialStore.load()
                            analytics.record("trial_milestone_presented", mapOf("milestone" to milestone.toString()))
                        }
                    },
                    onDismissTrialMilestone = { visibleMilestone = null },
                    onViewFreeSettings = { freeSettingsOpen = true },
                    onRetryOffer = billingManager::start,
                    onOpenPro = { if (onboardingStep == null) {
                        paywallEntry = if (BuildConfig.PRO_TRIAL_INTERNAL_TESTING && internalExpiryBanner && trial.exhausted) "expiry"
                            else if (visibleMilestone == 2) "completion_two" else "home"
                        paywallFeature = null
                        proPaywallOpen = true
                    } },
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
                        editorBase = null
                        pendingEditorAlarm = draft
                        if (onboardingStep == OnboardingStep.Add) onboardingStep = OnboardingStep.Time
                        route = "editor"
                    },
                    onEditAlarm = {
                        if (onboardingStep == null) {
                            editingAlarmId = it
                            editorBase = alarms.firstOrNull { alarm -> alarm.id == it }
                            pendingEditorAlarm = null
                            route = "editor"
                        }
                    },
                    onToggleAlarm = { id, enabled ->
                        if (onboardingStep == null) store.load().firstOrNull { it.id == id }?.let {
                            val reviewExpired = !effectivePro && trial.needsFreeReview(it)
                            val reviewPreview = trial.active && !it.customization.isFree && it.trialFallback == null
                            if (enabled && (reviewExpired || reviewPreview)) {
                                editorBase = it; editingAlarmId = it.id; pendingEditorAlarm = it; route = "editor"
                            } else saveAlarm(it.copy(enabled = enabled), it)
                        }
                    },
                    onDeleteAlarm = { id ->
                        if (onboardingStep == null) {
                            AlarmScheduler.cancel(context, id)
                            alarms = store.update { list -> list.filterNot { it.id == id } }
                            wakeProgress = wakeProgressStore.load()
                        }
                    },
                    onReorderAlarms = { reordered ->
                        if (onboardingStep == null) {
                            alarms = store.reorder(reordered.map { it.id })
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
                    trialActive = trial.active && proEntitlement == null,
                    onCustomize = if (effectivePro) ({
                        val saved = onboardingSavedAlarm
                        finishOnboarding()
                        if (saved != null) {
                            editorBase = saved; editingAlarmId = saved.id; pendingEditorAlarm = null; route = "editor"
                        }
                    }) else null,
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

    if (paywallVisible) {
        ProPaywallSheet(
            entitlement = proEntitlement,
            subscriptionAutoRenewing = billingState.subscriptionAutoRenewing,
            prices = billingState.offers.mapValues { it.value.formattedPrice },
            billingReady = billingState.connected,
            loadingPrices = billingState.loadingOffers,
            purchaseInProgress = billingState.purchasing,
            catalogSettled = billingState.catalogSettled,
            canPurchase = billingState.canPurchase && !internalNonPaying,
            internalPreviewOnly = internalNonPaying,
            verifyingPurchase = billingState.verifyingPurchases,
            pendingPayment = billingState.pendingPurchase,
            trialActive = trial.active && proEntitlement == null,
            trialExhausted = trial.exhausted && proEntitlement == null,
            entryFeature = paywallFeature,
            customizationSummary = if (trial.active || trial.exhausted) {
                if (alarms.none { it.trialCustomization != null || !it.customization.isFree }) "Your current routine can continue free unchanged."
                else "Pro keeps your customized poses, timing and sounds. Your alarms and streak remain available free."
            } else null,
            onViewFreeSettings = { proPaywallOpen = false; freeSettingsOpen = true },
            onPlanSelected = { analytics.record("plan_selected", mapOf("plan" to it.name.lowercase(), "entry_point" to paywallEntry)) },
            billingMessage = billingState.message,
            onDismiss = { proPaywallOpen = false },
            onPurchase = { selectedPlan ->
                if (!trialStore.internalNonPayingEnabled()) billingManager.purchase(context as Activity, selectedPlan)
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
            entitlement = billingState.entitlement,
            trialStatus = trialStatus,
            onRestorePurchases = billingManager::restorePurchases,
            subscriptionAutoRenewing = billingState.subscriptionAutoRenewing,
            weekStartsOnSunday = weekStartsOnSunday,
            onWeekStartsOnSundayChange = {
                weekStartsOnSunday = it
                settingsPreferences.edit().putBoolean("week_starts_on_sunday", it).apply()
            },
            onDismiss = { settingsSheetOpen = false },
            onOpenPro = {
                settingsSheetOpen = false
                paywallEntry = "settings"
                paywallFeature = null
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
            monetizationTesting = if (BuildConfig.PRO_TRIAL_INTERNAL_TESTING) {{
                MonetizationTestingSection(
                    trial = trial,
                    billing = billingState,
                    alarms = alarms,
                    enabled = !testingBusy && !testingBlocked(),
                    nonPaying = internalNonPaying,
                    expiryBanner = internalExpiryBanner,
                    message = testingMessage,
                    onNonPayingChange = { enabled ->
                        changeInternalTestState { trialStore.setInternalNonPaying(enabled, ::testingBlocked) }
                    },
                    onReset = {
                        changeInternalTestState(reset = true) {
                            trialStore.resetForInternalTesting(
                                paid = { billingManager.state.value.entitlement != null },
                                verifying = { billingManager.state.value.verifyingPurchases || billingManager.state.value.purchasing },
                                blocked = ::testingBlocked,
                            )
                        }
                    },
                    onAdvance = {
                        changeInternalTestState {
                            trialStore.advanceDayForInternalTesting(
                                now = java.time.ZonedDateTime.now(),
                                paid = { billingManager.state.value.entitlement != null },
                                verifying = { billingManager.state.value.verifyingPurchases || billingManager.state.value.purchasing },
                                blocked = ::testingBlocked,
                            )
                        }
                    },
                    onExpiryBannerChange = { banner ->
                        changeInternalTestState { trialStore.setInternalExpiryBanner(banner, ::testingBlocked) }
                    },
                )
            }} else null,
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

    if (freeSettingsOpen && !firedRoutine && firedAlarmId == null) AlertDialog(
        onDismissRequest = { freeSettingsOpen = false },
        title = { Text("Your free settings") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Only the first successful real alarm on each local date counts. After five, future alarms use these settings. Practice and stops use no allowance. No payment details or automatic charge.")
            alarms.forEach { alarm ->
                val free = alarm.customization.takeIf { it.isFree }
                    ?: alarm.trialFallback?.takeIf { it.isFree && alarm.trialCustomization == alarm.customization }
                val summary = if (!effectivePro && trial.needsFreeReview(alarm)) "Still uses the saved routine. Edit this alarm to confirm free settings."
                    else free?.summary ?: "No confirmed free settings. Choose your free routine before continuing."
                Text("${alarm.name}\n$summary")
                TextButton(onClick = {
                    freeSettingsOpen = false
                    editorBase = alarm; editingAlarmId = alarm.id; pendingEditorAlarm = null; route = "editor"
                }) { Text("Edit routine") }
            }
            if (alarms.isEmpty()) Text("Three free poses at 20 seconds each, with a free sound. Confirm the exact routine when saving Pro customization.")
        } },
        confirmButton = { TextButton(onClick = { freeSettingsOpen = false }) { Text("Done") } },
    )
    if (restoreOpen && proEntitlement != null && resumed && !permissionFlowInFlight && route != "camera" && !firedRoutine && firedAlarmId == null && onboardingStep == null &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked && !proPaywallOpen && !settingsSheetOpen && !privacySheetOpen && !freeSettingsOpen) AlertDialog(
        onDismissRequest = ::finishRestoration,
        title = { Text("Restore your trial routine") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            alarms.filter { it.trialOriginal != null }.forEach { alarm ->
                val unchanged = alarm.customization == alarm.trialFallback
                Row {
                    Checkbox(checked = alarm.id in restoreSelected, enabled = unchanged, onCheckedChange = { checked ->
                        restoreSelected = if (checked) restoreSelected + alarm.id else restoreSelected - alarm.id
                    })
                    Text("${alarm.name}\n${alarm.trialOriginal!!.summary}" + if (unchanged) "" else "\nYour newer edits are kept.")
                }
            }
        } },
        confirmButton = { TextButton(onClick = {
            alarms = trialStore.restore(restoreSelected) { billingManager.state.value.entitlement != null }
            analytics.record("trial_routine_restored")
            finishRestoration()
        }) { Text("Restore selected") } },
        dismissButton = { TextButton(onClick = ::finishRestoration) { Text("Keep current routine") } },
    )
}

private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions?sku=yoga_alarm_pro&package=com.lurremcfly.yogaalarm"
private const val PRIVACY_POLICY_URL = "https://www.yogaalarm.com/privacy"
