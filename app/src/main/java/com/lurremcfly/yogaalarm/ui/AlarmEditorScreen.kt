package com.lurremcfly.yogaalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.audio.AlarmAudio
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmCustomization
import com.lurremcfly.yogaalarm.model.AlarmSound
import com.lurremcfly.yogaalarm.model.PoseStep
import com.lurremcfly.yogaalarm.model.YogaPose
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmEditorScreen(
    initialAlarm: AlarmConfig,
    existingAlarmNames: List<String>,
    weekStartsOnSunday: Boolean,
    isPro: Boolean,
    onUpgrade: (String) -> Unit,
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
    trialActive: Boolean = false,
    needsFreeReview: Boolean = false,
) {
    BackHandler(onBack = onCancel)
    var draft by rememberSaveable(initialAlarm.id) { mutableStateOf(initialAlarm) }
    // Slot identity follows the card, including when multiple slots have identical poses.
    var poseCardIds by rememberSaveable(initialAlarm.id) { mutableStateOf(draft.routine.indices.toList()) }
    val poseCardBounds = remember { mutableStateMapOf<Int, Rect>() }
    var routineTop by remember { mutableFloatStateOf(0f) }
    var draggedPoseId by remember { mutableStateOf<Int?>(null) }
    var draggedPoseTop by remember { mutableFloatStateOf(0f) }
    var poseDragWindowY by remember { mutableFloatStateOf(0f) }
    var poseDragTravel by remember { mutableFloatStateOf(0f) }
    var hasDraggedPose by remember { mutableStateOf(false) }
    var routineBeforeDrag by remember { mutableStateOf(draft.routine) }
    var poseCardIdsBeforeDrag by remember { mutableStateOf(poseCardIds) }
    var durationSheetSlot by remember { mutableStateOf<Int?>(null) }
    var soundSheetOpen by rememberSaveable(initialAlarm.id) { mutableStateOf(onboardingStep == OnboardingStep.SoundChoice) }
    var snoozeSheetOpen by remember { mutableStateOf(false) }
    var fallbackReview by remember(initialAlarm.id) { mutableStateOf<AlarmCustomization?>(null) }
    var savedCustomization by rememberSaveable(initialAlarm.id) { mutableStateOf(initialAlarm.customization) }
    LaunchedEffect(initialAlarm.customization, isPro) {
        if (savedCustomization != initialAlarm.customization) {
            draggedPoseId = null
            durationSheetSlot = null
            soundSheetOpen = false
            fallbackReview = null
            poseCardBounds.clear()
            draft = draft.copy(
                routine = initialAlarm.routine,
                sound = initialAlarm.sound,
                trialFallback = initialAlarm.trialFallback,
                trialCustomization = initialAlarm.trialCustomization,
                trialOriginal = initialAlarm.trialOriginal,
            )
            poseCardIds = initialAlarm.routine.indices.toList()
            savedCustomization = initialAlarm.customization
        }
        if (!isPro) {
            draggedPoseId = null
            durationSheetSlot = null
            soundSheetOpen = false
            fallbackReview = null
        }
    }
    val editorScrollState = rememberScrollState()
    var editorContentHeight by remember { mutableIntStateOf(0) }
    var scrollToAddedPose by remember { mutableStateOf(false) }
    LaunchedEffect(editorContentHeight) {
        if (scrollToAddedPose) {
            // Wait until the new card and its spacing have updated the scroll range.
            withFrameNanos { }
            editorScrollState.animateScrollTo(editorScrollState.maxValue, tween(300))
            scrollToAddedPose = false
        }
    }
    val fontScale = LocalDensity.current.fontScale
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val tipHeight = onboardingTargets?.tipHeight ?: 0f
    val navigationBottom = WindowInsets.safeDrawing.getBottom(density)
    val statusTop = WindowInsets.safeDrawing.getTop(density)
    val haptics = LocalHapticFeedback.current
    val edgeSize = with(density) { 56.dp.toPx() }
    val actionClearance = with(density) { 90.dp.toPx() }

    fun movePose(from: Int, to: Int) {
        draft = draft.copy(routine = draft.routine.toMutableList().apply { add(to, removeAt(from)) })
        poseCardIds = poseCardIds.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun finishPoseDrag(cancelled: Boolean) {
        if (draggedPoseId == null) return
        if (cancelled) {
            draft = draft.copy(routine = routineBeforeDrag)
            poseCardIds = poseCardIdsBeforeDrag
        }
        draggedPoseId = null
    }

    LaunchedEffect(draggedPoseId) {
        val id = draggedPoseId ?: return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            if (!hasDraggedPose) continue
            val bounds = poseCardBounds[id] ?: continue
            val firstBounds = poseCardBounds[poseCardIds.first()] ?: continue
            val lastBounds = poseCardBounds[poseCardIds.last()] ?: continue
            val center = (draggedPoseTop + bounds.height / 2f).coerceIn(firstBounds.top, lastBounds.bottom - 1f)
            val targetId = poseCardIds.firstOrNull { candidate ->
                candidate != id && poseCardBounds[candidate]?.let { center >= it.top && center < it.bottom } == true
            }
            if (targetId != null) movePose(poseCardIds.indexOf(id), poseCardIds.indexOf(targetId))

            val edgeOverlap = when {
                poseDragWindowY < viewport.top + statusTop + edgeSize ->
                    poseDragWindowY - viewport.top - statusTop - edgeSize
                poseDragWindowY > viewport.bottom - navigationBottom - actionClearance - edgeSize ->
                    poseDragWindowY - viewport.bottom + navigationBottom + actionClearance + edgeSize
                else -> 0f
            }
            if (edgeOverlap != 0f) {
                draggedPoseTop += editorScrollState.scrollBy(edgeOverlap.coerceIn(-edgeSize, edgeSize) * 12f * seconds)
            }
        }
    }
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
            if (needsFreeReview) {
                Text(
                    "Your preview has ended. This alarm still uses its saved routine. Choose free settings to save changes.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MutedInk,
                    fontSize = 13.sp,
                )
            }
            if ((trialActive || needsFreeReview) && !draft.customization.isFree) {
                val fallback = draft.trialFallback?.takeIf { it.isFree } ?: draft.suggestedFreeFallback()
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(if (needsFreeReview) "Suggested free settings: ${fallback.summary}" else "After Pro preview: ${fallback.summary}", color = Forest, fontSize = 13.sp)
                    TextButton(onClick = { fallbackReview = fallback }) {
                        Text(if (trialActive && draft.trialFallback == fallback) "Review confirmed free settings" else "Review free settings")
                    }
                }
            }
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
                    .onSizeChanged { editorContentHeight = it.height }
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
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .onGloballyPositioned { routineTop = it.positionInWindow().y }
                            .pointerInput(initialAlarm.id, onboardingStep) {
                                if (onboardingStep != null) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { position ->
                                        poseCardIds.firstOrNull { poseCardBounds[it]?.contains(position) == true }?.let { id ->
                                            routineBeforeDrag = draft.routine
                                            poseCardIdsBeforeDrag = poseCardIds
                                            draggedPoseTop = poseCardBounds.getValue(id).top
                                            poseDragWindowY = routineTop + position.y
                                            poseDragTravel = 0f
                                            hasDraggedPose = false
                                            draggedPoseId = id
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        if (draggedPoseId != null) {
                                            change.consume()
                                            poseDragWindowY = routineTop + change.position.y
                                            draggedPoseTop += amount.y
                                            poseDragTravel += amount.y
                                            if (abs(poseDragTravel) > viewConfiguration.touchSlop) hasDraggedPose = true
                                        }
                                    },
                                    onDragEnd = { finishPoseDrag(cancelled = false) },
                                    onDragCancel = { finishPoseDrag(cancelled = true) },
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        draft.routine.forEachIndexed { index, step ->
                            val id = poseCardIds[index]
                            key(id) {
                                val isDragging = draggedPoseId == id
                                val top = poseCardBounds[id]?.top ?: 0f
                                val placementTop by animateFloatAsState(
                                    top,
                                    animationSpec = if (draggedPoseId != null && !isDragging) spring(stiffness = 400f) else snap(),
                                    label = "Pose placement",
                                )
                                val dragOffset by animateFloatAsState(
                                    if (isDragging) draggedPoseTop - top else 0f,
                                    animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
                                    label = "Pose drop",
                                )
                                val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "Pose lift")
                                Box(
                                    Modifier.fillMaxWidth()
                                        .onGloballyPositioned {
                                            poseCardBounds[id] = Rect(it.positionInParent(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                        }
                                        .zIndex(if (isDragging || abs(dragOffset) > 0.5f) 1f else 0f)
                                        .semantics {
                                            if (onboardingStep == null) {
                                                customActions = listOfNotNull(
                                                    if (index > 0) CustomAccessibilityAction("Move up") { movePose(index, index - 1); true } else null,
                                                    if (index < draft.routine.lastIndex) CustomAccessibilityAction("Move down") { movePose(index, index + 1); true } else null,
                                                )
                                            }
                                        },
                                ) {
                                    PoseCarouselCard(
                                        modifier = Modifier.graphicsLayer {
                                            translationY = placementTop - top + dragOffset
                                            scaleX = 1f + 0.02f * lift
                                            scaleY = scaleX
                                            shadowElevation = 12.dp.toPx() * lift
                                            shape = RoundedCornerShape(28.dp)
                                        },
                                        index = index,
                                        step = step,
                                        isPro = isPro,
                                        onUpgrade = { onUpgrade("pose") },
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
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isPro && draft.routine.size > 1) {
                            OutlinedButton(
                                enabled = onboardingStep == null,
                                onClick = {
                                    poseCardBounds.remove(poseCardIds.last())
                                    poseCardIds = poseCardIds.dropLast(1)
                                    draft = draft.copy(routine = draft.routine.dropLast(1))
                                },
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
                                    onUpgrade("count")
                                } else if (draft.routine.size < 10) {
                                    val nextPose = YogaPose.entries[draft.routine.size % YogaPose.entries.size]
                                    scrollToAddedPose = true
                                    poseCardIds = poseCardIds + ((poseCardIds.maxOrNull() ?: -1) + 1)
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
                if (needsFreeReview && !draft.customization.isFree) {
                    fallbackReview = draft.trialFallback?.takeIf { it.isFree } ?: draft.suggestedFreeFallback()
                } else if (!isPro && !draft.customization.isFree && draft.customization != initialAlarm.customization) {
                    onUpgrade(when {
                        !draft.sound.isFree -> "sound"
                        draft.routine.any { !it.pose.isFree } -> "pose"
                        draft.routine.size != 3 -> "count"
                        else -> "duration"
                    })
                } else if (trialActive && !draft.customization.isFree && draft.trialFallback?.isFree != true) {
                    fallbackReview = draft.suggestedFreeFallback()
                } else {
                    onSave(draft.copy(trialCustomization = if (trialActive && !draft.customization.isFree) draft.customization else null, name = draft.name.trim().ifEmpty {
                        AlarmConfig.defaultName(draft.hour, existingAlarmNames)
                    }))
                }
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
                trialActive = trialActive,
            )
        }
    }

    durationSheetSlot?.let { slotIndex ->
        val selectedStep = draft.routine.getOrNull(slotIndex) ?: return@let
        DurationPickerSheet(
            selectedDuration = selectedStep.durationSeconds,
            isPro = isPro,
            onUpgrade = {
                durationSheetSlot = null
                onUpgrade("duration")
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

    fallbackReview?.let { fallback ->
        ModalBottomSheet(onDismissRequest = { fallbackReview = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your free settings", color = Forest, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(if (needsFreeReview) "Saving applies these settings to future alarms. Already ringing or snoozed alarms keep their current routine."
                    else "After your fifth successful alarm day, future alarms use these settings unless you buy Pro. Already ringing or snoozed alarms keep their current routine.", color = MutedInk)
                Text("${fallback.routine.sumOf { it.durationSeconds }} seconds total · 3 poses × 20 seconds", fontWeight = FontWeight.Bold)
                if (fallback.routine.sumOf { it.durationSeconds } > draft.routine.sumOf { it.durationSeconds }) {
                    Text("This increases your hold time from ${draft.routine.sumOf { it.durationSeconds }} to 60 seconds.", color = Forest)
                }
                fallback.routine.forEachIndexed { index, step ->
                    Text("Pose ${index + 1}: ${step.pose.displayName}", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        YogaPose.entries.filter { it.isFree }.forEach { pose ->
                            TextButton(onClick = {
                                fallbackReview = fallback.copy(routine = fallback.routine.mapIndexed { slot, candidate -> if (slot == index) candidate.copy(pose = pose) else candidate })
                            }) { Text(pose.displayName, color = if (pose == step.pose) Forest else MutedInk) }
                        }
                    }
                }
                Text("Sound: ${fallback.sound.displayName}", fontWeight = FontWeight.SemiBold)
                AlarmSound.entries.filter { it.isFree }.forEach { sound ->
                    TextButton(onClick = { fallbackReview = fallback.copy(sound = sound) }) { Text(sound.displayName) }
                }
                if (trialActive && isPro) {
                    Button(onClick = {
                        val confirmed = draft.copy(trialFallback = fallback, trialCustomization = draft.customization, name = draft.name.trim().ifEmpty { AlarmConfig.defaultName(draft.hour, existingAlarmNames) })
                        draft = confirmed
                        fallbackReview = null
                        onSave(confirmed)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Confirm free settings & save") }
                }
                OutlinedButton(onClick = {
                    fallbackReview = null
                    onSave(draft.copy(routine = fallback.routine, sound = fallback.sound, trialFallback = null, trialCustomization = null,
                        name = draft.name.trim().ifEmpty { AlarmConfig.defaultName(draft.hour, existingAlarmNames) }))
                }, modifier = Modifier.fillMaxWidth()) { Text(if (needsFreeReview) "Save with free settings" else "Use free settings now") }
                TextButton(onClick = { fallbackReview = null }, modifier = Modifier.fillMaxWidth()) { Text("Keep editing") }
            }
        }
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
                onUpgrade("sound")
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
    modifier: Modifier = Modifier,
    guided: Boolean = false,
    guideSelected: Boolean = false,
    onboardingTargets: OnboardingTargets? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    val currentOnSelectPose by rememberUpdatedState(onSelectPose)
    val poses = if (guided && !isPro) YogaPose.entries.filter { it.isFree } else YogaPose.entries.toList()
    val pagerState = key(poses) {
        rememberPagerState(
            initialPage = poses.indexOf(step.pose).coerceAtLeast(0),
            pageCount = { poses.size },
        )
    }
    LaunchedEffect(pagerState, poses, step.pose) {
        val selectedPage = poses.indexOf(step.pose)
        if (selectedPage >= 0 && !pagerState.isScrollInProgress && pagerState.settledPage != selectedPage) {
            pagerState.scrollToPage(selectedPage)
        }
    }
    LaunchedEffect(pagerState, poses, isPro) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            poses.getOrNull(page)?.takeIf { isPro || it.isFree }?.let(currentOnSelectPose)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(listOf(OnboardingStep.Pose1, OnboardingStep.Pose2, OnboardingStep.Pose3).getOrNull(index)?.let {
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
                items(AlarmSound.entries.filter { onOnboardingStep == null || isPro || it.isFree }, key = { it.name }) { sound ->
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
