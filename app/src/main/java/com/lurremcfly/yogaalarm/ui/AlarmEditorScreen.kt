package com.lurremcfly.yogaalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.audio.AlarmAudio
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmSound
import com.lurremcfly.yogaalarm.model.PoseStep
import com.lurremcfly.yogaalarm.model.YogaPose
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmEditorScreen(
    initialAlarm: AlarmConfig,
    existingAlarmNames: List<String>,
    weekStartsOnSunday: Boolean,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit,
    onSave: (AlarmConfig) -> Unit,
    onTestRoutine: (AlarmConfig) -> Unit,
    onTestPose: (AlarmConfig, PoseStep) -> Unit,
    onboardingStep: OnboardingStep? = null,
    onboardingTargets: OnboardingTargets? = null,
    onOnboardingStep: (OnboardingStep, AlarmConfig) -> Unit = { _, _ -> },
    onSkipOnboarding: () -> Unit = {},
    onPractice: (AlarmConfig) -> Unit = {},
    practiceComplete: Boolean = false,
) {
    BackHandler(onBack = onCancel)
    var draft by rememberSaveable(initialAlarm.id) { mutableStateOf(initialAlarm) }
    var durationSheetSlot by remember { mutableStateOf<Int?>(null) }
    var soundSheetOpen by rememberSaveable(initialAlarm.id) { mutableStateOf(onboardingStep == OnboardingStep.SoundChoice) }
    var snoozeSheetOpen by remember { mutableStateOf(false) }
    val editorScrollState = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val tipHeight = onboardingTargets?.tipHeight ?: 0f
    val navigationBottom = WindowInsets.safeDrawing.getBottom(density)
    val statusTop = WindowInsets.safeDrawing.getTop(density)
    val availableEditorHeight = viewport.height - statusTop - navigationBottom
    var routineIntroductionReady by remember(onboardingStep) { mutableStateOf(false) }
    LaunchedEffect(onboardingStep, viewport.size, fontScale, statusTop, navigationBottom) {
        if (onboardingStep != OnboardingStep.Routine) return@LaunchedEffect
        withFrameNanos { }
        val target = onboardingTargets?.bounds?.get(OnboardingStep.Pose1)
        if (target != null) {
            val desired = viewport.top + statusTop + with(density) { 96.dp.toPx() }
            editorScrollState.animateScrollTo(
                (editorScrollState.value + target.top - desired).roundToInt().coerceAtLeast(0), tween(1_000),
            )
        }
        if (coroutineContext[MotionDurationScale]?.scaleFactor != 0f) delay(200)
        routineIntroductionReady = true
    }
    LaunchedEffect(onboardingStep, viewport.size, tipHeight, fontScale, statusTop, navigationBottom) {
        if (onboardingStep == null || onboardingStep.isPage || onboardingStep == OnboardingStep.SoundChoice || onboardingStep == OnboardingStep.Save) return@LaunchedEffect
        withFrameNanos { }
        if (onboardingStep == OnboardingStep.Time) {
            editorScrollState.animateScrollTo(0, tween(1_000))
        } else {
            val target = onboardingTargets?.bounds?.get(onboardingStep) ?: return@LaunchedEffect
            val top = viewport.top + statusTop + with(density) { 76.dp.toPx() }
            val bottom = viewport.bottom - navigationBottom - tipHeight - with(density) { 90.dp.toPx() }
            val desired = maxOf(top, (top + bottom - target.height) / 2f)
            editorScrollState.animateScrollTo(
                (editorScrollState.value + target.top - desired).roundToInt().coerceAtLeast(0), tween(1_000),
            )
        }
    }
    fun changeOnboardingStep(next: OnboardingStep) {
        if (next != OnboardingStep.Name) { focusManager.clearFocus(force = true); keyboard?.hide() }
        soundSheetOpen = next == OnboardingStep.SoundChoice
        onOnboardingStep(next, draft)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { viewport = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF3F5F3), Color(0xFFF1FBF5), Color(0xFFF3EEFF)),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(editorScrollState)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 90.dp),
        ) {
            AlarmTimePicker(
                hour = draft.hour,
                minute = draft.minute,
                onHourChange = { if (onboardingStep == null || onboardingStep == OnboardingStep.Time) draft = draft.copy(hour = it) },
                onMinuteChange = { if (onboardingStep == null || onboardingStep == OnboardingStep.Time) draft = draft.copy(minute = it) },
                modifier = Modifier.graphicsLayer {
                    // Short windows need the time picker to scroll out of the way.
                    translationY = if (availableEditorHeight >= with(density) { 600.dp.toPx() }) editorScrollState.value.toFloat() else 0f
                }.onboardingTarget(OnboardingStep.Time, onboardingTargets),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .onGloballyPositioned { onboardingTargets?.editorBounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }
                    .background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 22.dp),
            ) {
                    TextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it.take(40)) },
                        modifier = Modifier.fillMaxWidth().onboardingTarget(OnboardingStep.Name, onboardingTargets),
                        enabled = onboardingStep == null || onboardingStep == OnboardingStep.Name,
                        placeholder = { Text("Alarm name") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            disabledTextColor = Ink,
                            disabledPlaceholderColor = MutedInk,
                            focusedIndicatorColor = Forest,
                            unfocusedIndicatorColor = Hairline,
                            disabledIndicatorColor = Hairline,
                        ),
                    )
                    Spacer(Modifier.height(18.dp))
                    WeekdayPicker(
                        selectedDays = draft.weekdays,
                        weekStartsOnSunday = weekStartsOnSunday,
                        onSelectionChange = { draft = draft.copy(weekdays = it) },
                        enabled = onboardingStep == null || onboardingStep == OnboardingStep.Days,
                        onboardingTargets = onboardingTargets,
                    )
                    Spacer(Modifier.height(30.dp))
                    SettingRow("Sound", draft.sound.displayName, draft.soundEnabled,
                        modifier = Modifier.onboardingTarget(OnboardingStep.Sound, onboardingTargets),
                        enabled = onboardingStep == null || onboardingStep == OnboardingStep.Sound,
                        toggleEnabled = onboardingStep == null,
                        onClick = { soundSheetOpen = true; if (onboardingStep == OnboardingStep.Sound) changeOnboardingStep(OnboardingStep.SoundChoice) }) {
                        draft = draft.copy(soundEnabled = it)
                    }
                    HorizontalDivider(color = Hairline)
                    SettingRow(
                        "Snooze",
                        "${draft.snoozeMinutes} minutes, ${draft.snoozeCount} ${if (draft.snoozeCount == 1) "time" else "times"}",
                        draft.snoozeEnabled,
                        onClick = { snoozeSheetOpen = true },
                        enabled = onboardingStep == null,
                    ) {
                        draft = draft.copy(snoozeEnabled = it)
                    }
                    HorizontalDivider(color = Hairline)
                    SettingRow(
                        "Stop button",
                        "Allow manual alarm stop",
                        !draft.stopButtonDisabled,
                        enabled = onboardingStep == null,
                    ) {
                        draft = draft.copy(stopButtonDisabled = !it)
                    }
                    Spacer(Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = onboardingStep == null, role = androidx.compose.ui.semantics.Role.Button) { onTestRoutine(draft) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Test full routine", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Play all ${draft.routine.size} poses · ${draft.routine.sumOf { it.durationSeconds }} seconds",
                                color = MutedInk, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                color = Forest,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_play),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    draft.routine.forEachIndexed { index, step ->
                        PoseCarouselCard(
                            index = index,
                            step = step,
                            isPro = isPro,
                            onUpgrade = onUpgrade,
                            onSelectPose = { pose ->
                                draft = draft.copy(
                                    routine = draft.routine.mapIndexed { slot, candidate ->
                                        if (slot == index) candidate.copy(pose = pose) else candidate
                                    },
                                )
                            },
                            onSelectDuration = { durationSheetSlot = index },
                            onTryPose = { onTestPose(draft, step) },
                            guided = onboardingStep != null,
                            guideSelected = onboardingStep == listOf(OnboardingStep.Pose1, OnboardingStep.Pose2, OnboardingStep.Pose3).getOrNull(index),
                            onboardingTargets = onboardingTargets,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isPro && draft.routine.size > 1) {
                            OutlinedButton(
                                enabled = onboardingStep == null,
                                onClick = { draft = draft.copy(routine = draft.routine.dropLast(1)) },
                                modifier = Modifier.weight(1f).widthIn(min = 156.dp * fontScale),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text("− Remove pose", modifier = Modifier.padding(vertical = 4.dp),
                                    maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = {
                                if (!isPro) {
                                    onUpgrade()
                                } else if (draft.routine.size < 10) {
                                    val nextPose = YogaPose.entries[draft.routine.size % YogaPose.entries.size]
                                    draft = draft.copy(routine = draft.routine + PoseStep(nextPose))
                                }
                            },
                            enabled = onboardingStep == null && (!isPro || draft.routine.size < 10),
                            modifier = Modifier.weight(1f).widthIn(min = 156.dp * fontScale),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F6F1),
                                contentColor = Forest,
                                disabledContainerColor = Color(0xFFF1F6F1),
                                disabledContentColor = MutedInk,
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                if (isPro) "+ Add pose" else "+ Add pose   PRO",
                                modifier = Modifier.padding(vertical = 4.dp),
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
            }
        }
        EditorActions(
            modifier = Modifier.align(Alignment.BottomCenter),
            onCancel = onCancel,
            onSave = {
                onSave(draft.copy(name = draft.name.trim().ifEmpty {
                    AlarmConfig.defaultName(draft.hour, existingAlarmNames)
                }))
            },
            onboardingStep = onboardingStep,
            onboardingTargets = onboardingTargets,
        )
        if (onboardingStep != null && onboardingStep != OnboardingStep.SoundChoice && onboardingTargets != null) {
            OnboardingOverlay(onboardingStep, onboardingTargets, draft, practiceComplete,
                showRoutineIntroduction = routineIntroductionReady,
                onStep = ::changeOnboardingStep,
                onSkip = { soundSheetOpen = false; onSkipOnboarding() },
                onPractice = { onPractice(draft) },
                onTest = { onTestRoutine(draft) },
            )
        }
    }

    durationSheetSlot?.let { slotIndex ->
        DurationPickerSheet(
            selectedDuration = draft.routine[slotIndex].durationSeconds,
            isPro = isPro,
            onUpgrade = {
                durationSheetSlot = null
                onUpgrade()
            },
            onDismiss = { durationSheetSlot = null },
            onSelect = { duration ->
                draft = draft.copy(
                    routine = draft.routine.mapIndexed { index, step ->
                        if (index == slotIndex) step.copy(durationSeconds = duration) else step
                    },
                )
                durationSheetSlot = null
            },
        )
    }

    if (snoozeSheetOpen) {
        SnoozePickerSheet(
            selectedMinutes = draft.snoozeMinutes,
            selectedCount = draft.snoozeCount,
            onDismiss = { snoozeSheetOpen = false },
            onSelectMinutes = { draft = draft.copy(snoozeMinutes = it, snoozeEnabled = true) },
            onSelectCount = { draft = draft.copy(snoozeCount = it, snoozeEnabled = true) },
        )
    }

    if (soundSheetOpen) {
        SoundPickerSheet(
            selectedSound = draft.sound,
            vibrationEnabled = draft.vibrationEnabled,
            isPro = isPro,
            onUpgrade = {
                soundSheetOpen = false
                onUpgrade()
            },
            onDismiss = { soundSheetOpen = false; if (onboardingStep == OnboardingStep.SoundChoice) onOnboardingStep(OnboardingStep.Sound, draft) },
            onSelect = { sound ->
                draft = draft.copy(sound = sound, soundEnabled = true)
            },
            onVibrationChange = { draft = draft.copy(vibrationEnabled = it) },
            onboardingTargets = onboardingTargets,
            onOnboardingStep = if (onboardingStep == OnboardingStep.SoundChoice) ::changeOnboardingStep else null,
            onSkipOnboarding = { soundSheetOpen = false; onSkipOnboarding() },
        )
    }

}

@Composable
private fun AlarmTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(216.dp)
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        NumberWheel(value = hour, valueCount = 24, onValueChange = onHourChange, modifier = Modifier.weight(1f))
        Text(":", color = Ink, fontSize = 54.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        NumberWheel(value = minute, valueCount = 60, onValueChange = onMinuteChange, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    value: Int,
    valueCount: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = valueCount * 200
    val initialPage = valueCount * 100 + value
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onValueChange(((page % valueCount) + valueCount) % valueCount)
        }
    }
    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxHeight(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 72.dp),
        pageSize = PageSize.Fixed(72.dp),
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(8),
        ),
    ) { page ->
        val centered = page == pagerState.currentPage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(page) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = String.format(Locale.US, "%02d", page % valueCount),
                color = Ink.copy(alpha = if (centered) 1f else 0.18f),
                fontSize = if (centered) 64.sp else 45.sp,
                fontWeight = if (centered) FontWeight.Bold else FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun WeekdayPicker(
    selectedDays: Set<Int>,
    weekStartsOnSunday: Boolean,
    onSelectionChange: (Set<Int>) -> Unit,
    enabled: Boolean = true,
    onboardingTargets: OnboardingTargets? = null,
) {
    val days = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
    val orderedDays = if (weekStartsOnSunday) days.takeLast(1) + days.dropLast(1) else days
    Row(modifier = Modifier.fillMaxWidth().onboardingTarget(OnboardingStep.Days, onboardingTargets), horizontalArrangement = Arrangement.SpaceBetween) {
        orderedDays.forEach { (day, label) ->
            val selected = day in selectedDays
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clickable(enabled = enabled) {
                        onSelectionChange(if (selected) selectedDays - day else selectedDays + day)
                    },
                shape = CircleShape,
                color = if (selected) AlarmLime else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) Ink else MutedInk, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PoseCarouselCard(
    index: Int,
    step: PoseStep,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onSelectPose: (YogaPose) -> Unit,
    onSelectDuration: () -> Unit,
    onTryPose: () -> Unit,
    guided: Boolean = false,
    guideSelected: Boolean = false,
    onboardingTargets: OnboardingTargets? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    val poses = if (guided) YogaPose.entries.filter { it.isFree } else YogaPose.entries
    val pagerState = rememberPagerState(
        initialPage = poses.indexOf(step.pose).coerceAtLeast(0),
        pageCount = { poses.size },
    )
    LaunchedEffect(pagerState, isPro, guided) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            poses.getOrNull(page)?.takeIf { isPro || it.isFree }?.let(onSelectPose)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().then(listOf(OnboardingStep.Pose1, OnboardingStep.Pose2, OnboardingStep.Pose3).getOrNull(index)?.let {
            Modifier.onboardingTarget(it, onboardingTargets)
        } ?: Modifier),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFF1F6F1),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        BoxWithConstraints {
            val sidePadding = ((maxWidth - 184.dp * fontScale) / 2).coerceIn(16.dp, 44.dp)
            Column(Modifier.padding(vertical = 14.dp)) {
                Text(
                    "POSE ${index + 1}",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MutedInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !guided || guideSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(252.dp * fontScale.coerceAtLeast(1f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = sidePadding),
                    pageSpacing = 10.dp,
                ) { page ->
                    val pose = poses[page]
                    val unlocked = isPro || pose.isFree
                    val selected = page == pagerState.currentPage && unlocked
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (unlocked) 1f else 0.62f)
                            .clickable(enabled = !unlocked, onClick = onUpgrade),
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) Color(0xFFF5FFE6) else Color.White.copy(alpha = 0.82f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AlarmLime else Hairline),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                pose.displayName,
                                modifier = Modifier.height(40.dp * fontScale.coerceAtLeast(1f)),
                                color = Ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            PoseGlyph(pose, Modifier.size(132.dp))
                            Spacer(Modifier.height(8.dp))
                            when {
                                !unlocked -> Text("Tap to unlock Pro", color = MutedInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                selected -> FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.clickable(enabled = !guided, onClick = onSelectDuration),
                                        shape = RoundedCornerShape(50),
                                        color = Color.White,
                                    ) {
                                        Text(
                                            "${step.durationSeconds} sec",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                            color = Forest,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier.clickable(enabled = !guided, onClick = onTryPose),
                                        shape = RoundedCornerShape(50),
                                        color = Forest,
                                    ) {
                                        Text(
                                            "Try pose",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    poses.forEachIndexed { page, _ ->
                        Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .size(if (page == pagerState.currentPage) 7.dp else 5.dp)
                                .background(
                                    if (page == pagerState.currentPage) Forest else Hairline,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    toggleEnabled: Boolean = enabled,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MutedInk, fontSize = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = toggleEnabled,
            colors = yogaSwitchColors().copy(
                disabledCheckedThumbColor = Forest,
                disabledCheckedTrackColor = AlarmLime,
                disabledCheckedBorderColor = Color.Transparent,
                disabledUncheckedThumbColor = Color.White,
                disabledUncheckedTrackColor = Color(0xFFB9C1BB),
                disabledUncheckedBorderColor = Color.Transparent,
            ))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundPickerSheet(
    selectedSound: AlarmSound,
    vibrationEnabled: Boolean,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (AlarmSound) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onboardingTargets: OnboardingTargets? = null,
    onOnboardingStep: ((OnboardingStep) -> Unit)? = null,
    onSkipOnboarding: () -> Unit = {},
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = onOnboardingStep != null)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var previewPlayer by remember { mutableStateOf<AlarmAudio?>(null) }
    var previewing by remember { mutableStateOf<AlarmSound?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    fun stopPreview() {
        previewPlayer?.close()
        previewPlayer = null
        previewing = null
    }
    fun closeSheet(onClosed: () -> Unit) {
        if (closing) return
        closing = true
        stopPreview()
        scope.launch {
            try {
                sheetState.hide()
                if (!sheetState.isVisible) onClosed()
            } finally {
                closing = false
            }
        }
    }
    LaunchedEffect(previewing) {
        if (previewing != null) {
            delay(10_000)
            stopPreview()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stopPreview()
    }
    DisposableEffect(Unit) {
        onDispose { previewPlayer?.close() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, sheetState = sheetState,
        scrimColor = if (onOnboardingStep != null) Color(0xAA0D1913) else BottomSheetDefaults.ScrimColor,
        dragHandle = { if (onOnboardingStep == null) BottomSheetDefaults.DragHandle() }) {
        Box(if (onOnboardingStep != null) Modifier.fillMaxWidth().fillMaxHeight(0.92f) else Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            if (onOnboardingStep != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
            }
            Text("Sound & vibration", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(if (onOnboardingStep != null || isPro) "Choose your alarm sound." else "Upgrade to Pro to unlock the dimmed sounds.", color = MutedInk, fontSize = 13.sp)
            previewError?.let { Text(it, color = Color(0xFFB4232C)) }
            Spacer(Modifier.height(12.dp))
            SettingRow("Vibration", "Gentle pulse", vibrationEnabled, onCheckedChange = onVibrationChange)
            HorizontalDivider(color = Hairline)
            Spacer(Modifier.height(18.dp))
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).onboardingTarget(OnboardingStep.SoundChoice, onboardingTargets),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(AlarmSound.entries.filter { onOnboardingStep == null || it.isFree }, key = { it.name }) { sound ->
                    val locked = !sound.isFree && !isPro
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (locked) 0.45f else 1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (sound == selectedSound) Color(0xFFF5FFE6) else SoftSurface)
                            .border(1.dp, if (sound == selectedSound) AlarmLime else Hairline, RoundedCornerShape(22.dp))
                            .clickable {
                                if (locked) onUpgrade() else onSelect(sound)
                            }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sound.displayName, modifier = Modifier.weight(1f), color = Ink,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (previewing == sound) Forest else Color.White,
                                contentColor = if (previewing == sound) Color.White else Forest,
                            ),
                            onClick = {
                                if (locked) {
                                    onUpgrade()
                                    return@TextButton
                                }
                                val wasPreviewing = previewing == sound
                                stopPreview()
                                previewError = null
                                if (!wasPreviewing) {
                                    previewing = sound
                                    previewPlayer = AlarmAudio(context.applicationContext, sound) { message ->
                                        previewError = message
                                        previewing = null
                                    }.also {
                                        it.setLevel(0.7f)
                                        it.start()
                                    }
                                }
                            },
                        ) {
                            Text(if (previewing == sound) "🎵 Stop" else "🎵 Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (onOnboardingStep != null && onboardingTargets != null) {
            OnboardingOverlay(OnboardingStep.SoundChoice, onboardingTargets, null,
                onStep = { next -> closeSheet { onOnboardingStep(next) } },
                onSkip = { closeSheet(onSkipOnboarding) },
                onPractice = {}, onTest = {}, modifier = Modifier.matchParentSize(),
            )
        }
        }
    }
}

@Composable
private fun EditorActions(onCancel: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier,
    onboardingStep: OnboardingStep? = null, onboardingTargets: OnboardingTargets? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 62.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = onboardingStep == null, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.width(1.dp).height(30.dp).background(Hairline))
                TextButton(onClick = onSave, enabled = onboardingStep == null || onboardingStep == OnboardingStep.Save, modifier = Modifier.weight(1f).onboardingTarget(OnboardingStep.Save, onboardingTargets)) {
                    Text("Save", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosePickerSheet(
    selectedPose: YogaPose,
    onDismiss: () -> Unit,
    onSelect: (YogaPose) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoftSurface) {
        Column(Modifier.padding(bottom = 34.dp)) {
            Text("Choose a pose", modifier = Modifier.padding(horizontal = 22.dp), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Three poses are included free forever.", modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp), color = MutedInk)
            Spacer(Modifier.height(16.dp))
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(YogaPose.entries) { pose ->
                    val locked = !pose.isFree
                    Surface(
                        modifier = Modifier
                            .width(138.dp)
                            .height(178.dp)
                            .alpha(if (locked) 0.58f else 1f)
                            .clickable(enabled = !locked) { onSelect(pose) },
                        shape = RoundedCornerShape(24.dp),
                        color = if (pose == selectedPose) AlarmLime.copy(alpha = 0.5f) else Color.White,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth()) {
                                Text(if (locked) "PRO  🔒" else "FREE", color = if (locked) MutedInk else Forest, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            PoseGlyph(pose, Modifier.size(90.dp))
                            Text(pose.displayName, color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozePickerSheet(
    selectedMinutes: Int,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectCount: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoftSurface) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text("Snooze", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Choose how long and how many times.", color = MutedInk, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Text("SNOOZE TIME", color = MutedInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            SnoozeChoiceRow(
                options = listOf(5, 10, 15, 20),
                selected = selectedMinutes,
                suffix = "min",
                onSelect = onSelectMinutes,
            )
            Spacer(Modifier.height(24.dp))
            Text("NUMBER OF SNOOZES", color = MutedInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            SnoozeChoiceRow(
                options = listOf(1, 2, 3, 5),
                selected = selectedCount,
                suffix = "×",
                onSelect = onSelectCount,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Done", modifier = Modifier.padding(vertical = 5.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SnoozeChoiceRow(
    options: List<Int>,
    selected: Int,
    suffix: String,
    onSelect: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) AlarmLime.copy(alpha = 0.55f) else Color.White,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Forest) else null,
            ) {
                Text(
                    "$option $suffix",
                    modifier = Modifier.padding(vertical = 15.dp),
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPickerSheet(
    selectedDuration: Int,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val durations = listOf(5, 10, 15, 20, 30, 45, 60)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoftSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp)) {
            Text("Hold duration", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("20 seconds is included in the free routine.", color = MutedInk, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            durations.forEach { duration ->
                val locked = duration != 20 && !isPro
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (locked) onUpgrade() else onSelect(duration)
                        }
                        .padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$duration seconds", modifier = Modifier.weight(1f), color = if (locked) MutedInk else Ink, fontSize = 18.sp, fontWeight = if (duration == selectedDuration) FontWeight.Bold else FontWeight.Normal)
                    Text(
                        text = when {
                            locked -> "PRO  🔒"
                            duration == selectedDuration -> "✓"
                            else -> ""
                        },
                        color = Forest,
                        fontWeight = FontWeight.Bold,
                    )
                }
                HorizontalDivider(color = Hairline)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
