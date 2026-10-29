package com.lurremcfly.yogaalarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.model.AlarmConfig
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmHomeScreen(
    alarms: List<AlarmConfig>,
    streakDays: Int,
    previousStreakDays: Int,
    bestStreakDays: Int,
    today: LocalDate,
    completedDates: Set<LocalDate>,
    pausedDates: Set<LocalDate>,
    scheduledDates: Set<LocalDate>,
    weekStartsOnSunday: Boolean,
    onStreakPresented: () -> Unit,
    savedAlarmId: Long?,
    onSavedAlarmPresented: (Long) -> Unit,
    isPro: Boolean,
    onOpenPro: () -> Unit,
    onOpenSettings: () -> Unit,
    cameraReady: Boolean,
    notificationsReady: Boolean,
    fullScreenReady: Boolean,
    exactAlarmsReady: Boolean,
    onFixCamera: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixFullScreen: () -> Unit,
    onFixExactAlarms: () -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onReorderAlarms: (List<AlarmConfig>) -> Unit,
    reorderingEnabled: Boolean = true,
    onboardingTargets: OnboardingTargets? = null,
    trialStatus: String? = null,
    trialMilestone: Int? = null,
    offerAvailable: Boolean = false,
    onTrialMilestonePresented: (Int) -> Unit = {},
    onDismissTrialMilestone: () -> Unit = {},
    onViewFreeSettings: () -> Unit = {},
    onRetryOffer: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var displayedAlarms by remember(alarms) { mutableStateOf(alarms) }
    var draggedAlarmId by remember { mutableStateOf<Long?>(null) }
    var draggedTop by remember { mutableFloatStateOf(0f) }
    var dragTravel by remember { mutableFloatStateOf(0f) }
    var hasDragged by remember { mutableStateOf(false) }
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var deleteBounds by remember { mutableStateOf(Rect.Zero) }
    val isOverDelete by remember {
        derivedStateOf { draggedAlarmId != null && hasDragged && deleteBounds.contains(dragPosition) }
    }
    val haptics = LocalHapticFeedback.current
    val edgeSize = with(LocalDensity.current) { 56.dp.toPx() }
    val buttonClearance = with(LocalDensity.current) { 80.dp.toPx() }

    fun finishDrag(cancelled: Boolean) {
        val id = draggedAlarmId ?: return
        val deleteOnRelease = isOverDelete
        draggedAlarmId = null
        if (cancelled) {
            displayedAlarms = alarms
        } else if (deleteOnRelease) {
            onDeleteAlarm(id)
        } else if (hasDragged) {
            if (displayedAlarms != alarms) onReorderAlarms(displayedAlarms)
        }
    }

    LaunchedEffect(isOverDelete) {
        if (isOverDelete) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(draggedAlarmId) {
        val id = draggedAlarmId ?: return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            if (!hasDragged || isOverDelete) continue
            val layout = listState.layoutInfo
            val visibleAlarms = layout.visibleItemsInfo.filter { it.key is Long }
            val dragged = visibleAlarms.firstOrNull { it.key == id } ?: continue
            val center = (draggedTop + dragged.size / 2f).coerceIn(
                visibleAlarms.first().offset.toFloat(),
                (visibleAlarms.last().offset + visibleAlarms.last().size - 1).toFloat(),
            )
            val target = visibleAlarms.firstOrNull {
                it.key != id && center >= it.offset && center < it.offset + it.size
            }
            if (target != null) {
                val from = displayedAlarms.indexOfFirst { it.id == id }
                val to = displayedAlarms.indexOfFirst { it.id == target.key }
                if (from >= 0 && to >= 0 && from != to) {
                    // Keep the viewport steady when its first visible alarm changes position.
                    listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    displayedAlarms = displayedAlarms.toMutableList().apply { add(to, removeAt(from)) }
                }
            }
            val edgeOverlap = when {
                draggedTop < layout.viewportStartOffset + edgeSize ->
                    draggedTop - layout.viewportStartOffset - edgeSize
                draggedTop + dragged.size > layout.viewportEndOffset - buttonClearance - edgeSize ->
                    draggedTop + dragged.size - layout.viewportEndOffset + buttonClearance + edgeSize
                else -> 0f
            }
            if (edgeOverlap != 0f) {
                listState.scrollBy(edgeOverlap.coerceIn(-edgeSize, edgeSize) * 12f * seconds)
            }
        }
    }
    LaunchedEffect(cameraReady, notificationsReady, fullScreenReady, exactAlarmsReady) {
        if (cameraReady && notificationsReady && fullScreenReady && exactAlarmsReady) {
            onboardingTargets?.readinessBounds = Rect.Zero
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF1FAF6), Color(0xFFF5F1FF), Color(0xFFEAF4FF)),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)
                .onGloballyPositioned { listOrigin = it.positionInWindow() }
                .pointerInput(alarms, reorderingEnabled) {
                    if (!reorderingEnabled) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                it.key is Long && position.y >= it.offset && position.y < it.offset + it.size
                            }?.let {
                                draggedTop = it.offset.toFloat()
                                dragPosition = listOrigin + position
                                dragTravel = 0f
                                hasDragged = false
                                draggedAlarmId = it.key as Long
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, amount ->
                            if (draggedAlarmId != null) {
                                change.consume()
                                dragPosition = listOrigin + change.position
                                draggedTop += amount.y
                                dragTravel += amount.y
                                if (abs(dragTravel) > viewConfiguration.touchSlop) hasDragged = true
                            }
                        },
                        onDragEnd = { finishDrag(cancelled = false) },
                        onDragCancel = { finishDrag(cancelled = true) },
                    )
                },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 22.dp,
                top = 40.dp,
                end = 22.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactHeader = maxWidth < 340.dp
                    val headerFontSize = when {
                        maxWidth < 300.dp -> 22.sp
                        compactHeader -> 24.sp
                        else -> 28.sp
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Your alarms",
                            modifier = Modifier.align(Alignment.CenterVertically),
                            color = Ink,
                            fontSize = headerFontSize,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!isPro) Surface(
                                modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onOpenPro),
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        if (trialStatus != null) "KEEP PRO" else "GET PRO",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        color = Forest,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings",
                                    tint = MutedInk, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            if (!isPro && trialStatus != null) item(key = "preview") {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(trialStatus, color = Forest, fontWeight = FontWeight.SemiBold)
                    if (trialMilestone == 2 && offerAvailable) {
                        LaunchedEffect(trialMilestone) { onTrialMilestonePresented(2) }
                        Text("Make this routine yours. Keep Pro with a one-time purchase or subscription.", color = MutedInk)
                        Row {
                            TextButton(onClick = onOpenPro) { Text("Keep Pro") }
                            TextButton(onClick = onDismissTrialMilestone) { Text("Not now") }
                        }
                    } else if (trialMilestone == 4) {
                        LaunchedEffect(trialMilestone) { onTrialMilestonePresented(4) }
                        Text("1 Pro alarm day left. After your next qualifying completion, future alarms use your free routine unless you buy Pro.", color = MutedInk)
                        TextButton(onClick = onDismissTrialMilestone) { Text("Got it") }
                    }
                    TextButton(onClick = onViewFreeSettings) { Text("View free settings") }
                    if (trialMilestone in listOf(2, 5) && !offerAvailable) {
                        Text("Purchase offers are unavailable. Your alarms remain ready.", color = MutedInk)
                        TextButton(onClick = onRetryOffer) { Text("Retry offers") }
                    }
                }
            }
            item(key = "streak") {
                Column {
                    StreakCard(streakDays, previousStreakDays, bestStreakDays, today, completedDates, pausedDates, scheduledDates, weekStartsOnSunday, onStreakPresented)
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (!cameraReady || !notificationsReady || !fullScreenReady || !exactAlarmsReady) {
                item {
                    AlarmReadinessCard(
                        cameraReady = cameraReady,
                        notificationsReady = notificationsReady,
                        fullScreenReady = fullScreenReady,
                        exactAlarmsReady = exactAlarmsReady,
                        onFixCamera = onFixCamera,
                        onFixNotifications = onFixNotifications,
                        onFixFullScreen = onFixFullScreen,
                        onFixExactAlarms = onFixExactAlarms,
                        modifier = Modifier.onGloballyPositioned {
                            onboardingTargets?.readinessBounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                        },
                    )
                }
            }

            if (alarms.isEmpty()) {
                item { EmptyAlarmCard(onAddAlarm) }
            } else {
                items(displayedAlarms, key = { it.id }) { alarm ->
                    val isDragging = alarm.id == draggedAlarmId
                    val dragOffset by animateFloatAsState(
                        targetValue = if (isDragging) {
                            draggedTop - (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == alarm.id }?.offset ?: 0)
                        } else 0f,
                        animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
                        label = "Alarm drop",
                    )
                    val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "Alarm lift")
                    val arrival = remember(alarm.id) { Animatable(if (alarm.id == savedAlarmId) 0f else 1f) }
                    LaunchedEffect(alarm.id) {
                        if (alarm.id == savedAlarmId) {
                            onSavedAlarmPresented(alarm.id)
                            arrival.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
                        }
                    }
                    val arrivalOffset = with(LocalDensity.current) { 24.dp.toPx() }
                    AlarmSummaryCard(
                        modifier = Modifier
                            .animateItem(placementSpec = if (isDragging) null else spring(stiffness = 400f))
                            .zIndex(if (isDragging || abs(dragOffset) > 0.5f) 1f else 0f)
                            .graphicsLayer {
                                alpha = arrival.value.coerceIn(0f, 1f)
                                translationY = dragOffset + arrivalOffset * (1f - arrival.value)
                                scaleX = (0.97f + 0.03f * arrival.value) * (1f + 0.02f * lift)
                                scaleY = scaleX
                                shadowElevation = 12.dp.toPx() * lift
                                shape = RoundedCornerShape(34.dp)
                            }.semantics {
                            if (reorderingEnabled) {
                                val index = displayedAlarms.indexOfFirst { it.id == alarm.id }
                                customActions = listOfNotNull(
                                    if (index > 0) CustomAccessibilityAction("Move up") {
                                        onReorderAlarms(displayedAlarms.toMutableList().apply { add(index - 1, removeAt(index)) })
                                        true
                                    } else null,
                                    if (index < displayedAlarms.lastIndex) CustomAccessibilityAction("Move down") {
                                        onReorderAlarms(displayedAlarms.toMutableList().apply { add(index + 1, removeAt(index)) })
                                        true
                                    } else null,
                                    CustomAccessibilityAction("Delete alarm") { onDeleteAlarm(alarm.id); true },
                                )
                            }
                        },
                        alarm = alarm,
                        onClick = { onEditAlarm(alarm.id) },
                        onToggle = { onToggleAlarm(alarm.id, it) },
                    )
                }
            }

        }
        val actionColor by animateColorAsState(
            when {
                isOverDelete -> Color(0xFF861923)
                draggedAlarmId != null -> Color(0xFFB4232C)
                else -> AlarmLime
            },
            tween(150), label = "Alarm action color",
        )
        Button(
            onClick = { if (draggedAlarmId == null) onAddAlarm() },
            modifier = Modifier.align(Alignment.BottomCenter)
                .zIndex(2f)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp)
                .heightIn(min = 56.dp)
                .onGloballyPositioned { deleteBounds = it.boundsInWindow() }
                .semantics {
                    if (draggedAlarmId != null) {
                        stateDescription = if (isOverDelete) "Release to delete alarm" else "Drag alarm here to delete"
                    }
                }
                .onboardingTarget(OnboardingStep.Add, onboardingTargets),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = actionColor,
                contentColor = if (draggedAlarmId != null) Color.White else Forest,
            ),
            border = if (isOverDelete) BorderStroke(2.dp, Color.White) else null,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isOverDelete) 8.dp else 4.dp),
        ) {
            Icon(
                painterResource(if (draggedAlarmId != null) R.drawable.ic_delete else R.drawable.ic_add),
                contentDescription = null, modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (draggedAlarmId != null) "Delete alarm" else "Add alarm", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreakCard(
    streakDays: Int,
    previousStreakDays: Int,
    bestStreakDays: Int,
    today: LocalDate,
    completedDates: Set<LocalDate>,
    pausedDates: Set<LocalDate>,
    scheduledDates: Set<LocalDate>,
    weekStartsOnSunday: Boolean,
    onPresented: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var historyMonths by rememberSaveable { mutableIntStateOf(3) }
    var displayedDays by rememberSaveable { mutableIntStateOf(minOf(previousStreakDays, streakDays)) }
    var bounceRequest by remember { mutableIntStateOf(0) }
    val flameRotation = remember { Animatable(0f) }
    LaunchedEffect(streakDays) {
        val earned = streakDays > previousStreakDays
        onPresented()
        if (earned && coroutineContext[MotionDurationScale]?.scaleFactor != 0f) {
            delay(100)
            displayedDays = streakDays
            bounceRequest++
        } else {
            displayedDays = streakDays
        }
    }
    LaunchedEffect(bounceRequest) {
        if (bounceRequest == 0) return@LaunchedEffect
        flameRotation.animateTo(-18f, tween(100))
        flameRotation.animateTo(18f, tween(180))
        flameRotation.animateTo(0f, spring(dampingRatio = 0.45f))
    }
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        Column {
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(80.dp).clickable(
                    onClickLabel = if (expanded) "Hide streak history" else "Show streak history",
                ) { expanded = !expanded }.semantics {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }.padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val labelHeight = (16f * density.fontScale).coerceAtMost(24f).dp
                val rowGap = 4.dp
                val markerSize = minOf(
                    when {
                        density.fontScale >= 1.4f -> 34.dp
                        density.fontScale >= 1.15f -> 32.dp
                        else -> 28.dp
                    },
                    maxHeight - labelHeight - rowGap,
                )
                val labelFontSize = with(density) { minOf(12.sp.toPx(), 22.dp.toPx()).toSp() }
                val countFontSize = with(density) { minOf(28.sp.toPx(), markerSize.toPx()).toSp() }
                val labelStyle = TextStyle(
                    fontSize = labelFontSize, lineHeight = with(density) { labelHeight.toSp() },
                    fontWeight = FontWeight.Medium, platformStyle = PlatformTextStyle(includeFontPadding = false),
                )
                val countStyle = TextStyle(
                    fontSize = countFontSize, lineHeight = countFontSize,
                    fontWeight = FontWeight.Bold, platformStyle = PlatformTextStyle(includeFontPadding = false),
                )
                val flameSize = minOf(24.dp, markerSize - 4.dp)
                val countWidth = with(density) {
                    textMeasurer.measure(maxOf(streakDays, previousStreakDays).toString(), countStyle).size.width.toDp()
                } + flameSize + 6.dp
                val markerGap = 6.dp
                val groupGap = 12.dp
                val fullLabelWidth = with(density) { textMeasurer.measure("day streak", labelStyle).size.width.toDp() }
                val label = if (maxOf(fullLabelWidth, countWidth) + groupGap + markerSize * 5 + markerGap * 4 <= maxWidth) {
                    "day streak"
                } else "streak"
                val statWidth = maxOf(countWidth, with(density) { textMeasurer.measure(label, labelStyle).size.width.toDp() })
                val availableDaysWidth = maxWidth - statWidth - groupGap * 2 - 16.dp
                val dayCount = listOf(7, 5, 3, 1).firstOrNull {
                    markerSize * it + markerGap * (it - 1) <= availableDaysWidth
                } ?: 1
                val firstOffset = if (dayCount == 7) -4L else -(dayCount / 2).toLong()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(groupGap)) {
                    Column(
                        Modifier.width(statWidth).clearAndSetSemantics { contentDescription = "$streakDays-day yoga streak" },
                        verticalArrangement = Arrangement.spacedBy(rowGap),
                    ) {
                        Text(label, color = MutedInk, style = labelStyle, modifier = Modifier.height(labelHeight), maxLines = 1)
                        Row(Modifier.height(markerSize), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(flameSize).height(markerSize), contentAlignment = Alignment.Center) {
                                Text("🔥", fontSize = with(density) { flameSize.toSp() },
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)),
                                    maxLines = 1, overflow = TextOverflow.Visible,
                                    // Emoji font metrics can exceed the nominal glyph size.
                                    modifier = Modifier.wrapContentSize(unbounded = true)
                                        .graphicsLayer { rotationZ = flameRotation.value })
                            }
                            Spacer(Modifier.width(6.dp))
                            AnimatedContent(
                                targetState = displayedDays,
                                transitionSpec = {
                                    (slideInVertically(tween(350)) { it } + fadeIn(tween(250))) togetherWith
                                        (slideOutVertically(tween(350)) { -it } + fadeOut(tween(200)))
                                }, label = "Earned streak count",
                            ) { days ->
                                Text(days.toString(), color = Ink, style = countStyle, maxLines = 1)
                            }
                        }
                    }
                    if (expanded) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Personal best", color = MutedInk, style = labelStyle, maxLines = 1)
                            Text("$bestStreakDays days", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (offset in firstOffset until firstOffset + dayCount) {
                            val date = today.plusDays(offset)
                            val completed = date <= today && date in completedDates
                            val restDay = !completed && date in pausedDates
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(rowGap),
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = "${date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL))}, " +
                                        (if (date == today) "today, " else "") +
                                        (if (completed) "yoga completed" else if (restDay) "rest day, no alarm scheduled"
                                        else if (date > today) "upcoming" else "yoga not completed")
                                },
                            ) {
                                Text(
                                    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                                    color = if (date == today) Forest else MutedInk,
                                    style = labelStyle.copy(fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium),
                                    modifier = Modifier.height(labelHeight), maxLines = 1,
                                )
                                Box(
                                    Modifier.size(markerSize).clip(RoundedCornerShape(8.dp))
                                        .background(when {
                                            completed -> Color(0xFF81BD50)
                                            restDay -> Color(0xFFF5BF42)
                                            else -> Color.Transparent
                                        })
                                        .border(if (date == today) 2.dp else 1.dp,
                                            when {
                                                completed -> Color(0xFF81BD50)
                                                date == today -> Forest
                                                restDay -> Color(0xFFF5BF42)
                                                else -> Hairline
                                            },
                                            RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (completed) Text("✓", color = Color.White, fontSize = with(density) { (markerSize * 0.7f).toSp() }, fontWeight = FontWeight.Bold)
                                    else if (restDay) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(markerSize * 0.12f)) {
                                            repeat(2) {
                                                Box(Modifier.width(markerSize * 0.12f).height(markerSize * 0.46f)
                                                    .background(Forest, RoundedCornerShape(1.dp)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(if (expanded) "⌃" else "⌄", color = MutedInk, fontSize = 18.sp,
                        modifier = Modifier.width(16.dp).clearAndSetSemantics {})
                }
            }
            if (expanded) StreakHistory(
                today = today,
                completedDates = completedDates,
                pausedDates = pausedDates,
                scheduledDates = scheduledDates,
                weekStartsOnSunday = weekStartsOnSunday,
                months = historyMonths,
                onMonthsChange = { historyMonths = it },
            )
        }
    }
}

@Composable
internal fun AlarmReadinessCard(
    cameraReady: Boolean,
    notificationsReady: Boolean,
    fullScreenReady: Boolean,
    exactAlarmsReady: Boolean,
    onFixCamera: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixFullScreen: () -> Unit,
    onFixExactAlarms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readiness = listOf(cameraReady, notificationsReady, fullScreenReady, exactAlarmsReady)
    val readyCount = readiness.count { it }
    val nextIncompleteIndex = readiness.indexOfFirst { !it }
    val pointerOffset = remember { Animatable(0f) }
    var pressedPointerIndex by remember { mutableStateOf<Int?>(null) }
    val nudgeDistance = with(LocalDensity.current) { 6.dp.toPx() }

    LaunchedEffect(nextIncompleteIndex, pressedPointerIndex) {
        pointerOffset.snapTo(0f)
        if (nextIncompleteIndex >= 0 && pressedPointerIndex != nextIncompleteIndex) {
            while (true) {
                pointerOffset.animateTo(nudgeDistance, tween(durationMillis = 160))
                pointerOffset.animateTo(0f, tween(durationMillis = 160))
                delay(600)
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Forest),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text("Finish alarm setup · $readyCount/4", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap every green button below so your alarm can wake you reliably.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            ReadinessRow("Camera", "Verifies your poses", "Allow", cameraReady, nextIncompleteIndex == 0, { pointerOffset.value }) {
                if (nextIncompleteIndex == 0) pressedPointerIndex = 0
                onFixCamera()
            }
            ReadinessRow("Notifications", "Shows a ringing alarm", "Allow", notificationsReady, nextIncompleteIndex == 1, { pointerOffset.value }) {
                if (nextIncompleteIndex == 1) pressedPointerIndex = 1
                onFixNotifications()
            }
            ReadinessRow("Full-screen alarms", "Opens over your lock screen", "Enable", fullScreenReady, nextIncompleteIndex == 2, { pointerOffset.value }) {
                if (nextIncompleteIndex == 2) pressedPointerIndex = 2
                onFixFullScreen()
            }
            ReadinessRow("Exact alarms", "Keeps alarms on time", "Enable", exactAlarmsReady, nextIncompleteIndex == 3, { pointerOffset.value }) {
                if (nextIncompleteIndex == 3) pressedPointerIndex = 3
                onFixExactAlarms()
            }
        }
    }
}

@Composable
private fun ReadinessRow(
    title: String,
    detail: String,
    action: String,
    completed: Boolean,
    showPointer: Boolean,
    pointerOffset: () -> Float,
    onClick: () -> Unit,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)

    Surface(
        modifier = if (completed) rowModifier else rowModifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
            }
            if (completed) {
                Text("✓", color = AlarmLime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            } else {
                if (showPointer) {
                    Text(
                        "👉",
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .graphicsLayer { translationX = pointerOffset() }
                            .clearAndSetSemantics { },
                        fontSize = 18.sp,
                    )
                }
                Surface(shape = RoundedCornerShape(999.dp), color = AlarmLime) {
                    Text(
                        "$action  →",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Forest,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAlarmCard(onAddAlarm: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddAlarm),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.82f)),
    ) {
        Column(Modifier.padding(28.dp)) {
            Text("No alarms yet", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Create a calm one-minute routine for tomorrow morning.", color = MutedInk)
            Spacer(Modifier.height(20.dp))
            Text("Create alarm  →", color = Forest, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AlarmSummaryCard(
    modifier: Modifier,
    alarm: AlarmConfig,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val nameColor by animateColorAsState(
        if (alarm.enabled) Ink else MutedInk.copy(alpha = 0.55f), tween(250), label = "Alarm name color",
    )
    val timeColor by animateColorAsState(
        if (alarm.enabled) Ink else MutedInk.copy(alpha = 0.45f), tween(250), label = "Alarm time color",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(34.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = alarm.name,
                        color = nameColor,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute),
                        color = timeColor,
                        fontSize = 48.sp,
                        lineHeight = 54.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    colors = yogaSwitchColors(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(weekdaySummary(alarm.weekdays), color = MutedInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Hairline)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${alarm.routine.joinToString(" · ") { it.pose.displayName }}  ·  ${alarm.routine.sumOf { it.durationSeconds }} sec",
                color = Forest,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
    }
}

private fun weekdaySummary(days: Set<Int>): String = when {
    days == setOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    days.isEmpty() -> "Once"
    else -> listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        .filter { it.first in days }
        .joinToString(", ") { it.second }
}
