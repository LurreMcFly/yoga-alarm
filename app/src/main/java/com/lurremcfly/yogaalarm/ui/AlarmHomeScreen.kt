package com.lurremcfly.yogaalarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.model.AlarmConfig
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmHomeScreen(
    alarms: List<AlarmConfig>,
    streakDays: Int,
    previousStreakDays: Int,
    today: LocalDate,
    completedDates: Set<LocalDate>,
    scheduledDates: Set<LocalDate>,
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
    onboardingTargets: OnboardingTargets? = null,
) {
    var selectedAlarmId by remember { mutableStateOf<Long?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF1FAF6), Color(0xFFF5F1FF), Color(0xFFEAF4FF)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
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
                                        "GET PRO",
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

            item(key = "streak") {
                Column {
                    StreakCard(streakDays, previousStreakDays, today, completedDates, scheduledDates, onStreakPresented)
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
                    )
                }
            }

            if (alarms.isEmpty()) {
                item { EmptyAlarmCard(onAddAlarm) }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    val arrival = remember(alarm.id) { Animatable(if (alarm.id == savedAlarmId) 0f else 1f) }
                    LaunchedEffect(alarm.id) {
                        if (alarm.id == savedAlarmId) {
                            onSavedAlarmPresented(alarm.id)
                            arrival.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
                        }
                    }
                    val arrivalOffset = with(LocalDensity.current) { 24.dp.toPx() }
                    AlarmSummaryCard(
                        modifier = Modifier.animateItem().graphicsLayer {
                            alpha = arrival.value.coerceIn(0f, 1f)
                            translationY = arrivalOffset * (1f - arrival.value)
                            scaleX = 0.97f + 0.03f * arrival.value
                            scaleY = scaleX
                        },
                        alarm = alarm,
                        selected = alarm.id == selectedAlarmId,
                        onClick = { onEditAlarm(alarm.id) },
                        onLongClick = { selectedAlarmId = alarm.id },
                        onToggle = { onToggleAlarm(alarm.id, it) },
                    )
                }
            }

        }
        Button(
            onClick = onAddAlarm,
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp)
                .heightIn(min = 56.dp)
                .onboardingTarget(OnboardingStep.Add, onboardingTargets),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = AlarmLime, contentColor = Forest),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add alarm", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        alarms.firstOrNull { it.id == selectedAlarmId }?.let { alarm ->
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { selectedAlarmId = null },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 80.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    shadowElevation = 10.dp,
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        TextButton(onClick = {
                            onToggleAlarm(alarm.id, !alarm.enabled)
                            selectedAlarmId = null
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(R.drawable.ic_alarm), contentDescription = null,
                                    tint = Forest, modifier = Modifier.size(26.dp))
                                Text(if (alarm.enabled) "Turn off" else "Turn on", color = Forest)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = {
                            onDeleteAlarm(alarm.id)
                            selectedAlarmId = null
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = null,
                                    tint = Color(0xFFB4232C), modifier = Modifier.size(26.dp))
                                Text("Delete", color = Color(0xFFB4232C))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreakCard(
    streakDays: Int,
    previousStreakDays: Int,
    today: LocalDate,
    completedDates: Set<LocalDate>,
    scheduledDates: Set<LocalDate>,
    onPresented: () -> Unit,
) {
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
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = streakDays > 0,
            onClickLabel = "Celebrate streak",
        ) { bounceRequest++ },
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 14.dp, vertical = 10.dp),
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
            val availableDaysWidth = maxWidth - statWidth - groupGap
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
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (offset in firstOffset until firstOffset + dayCount) {
                        val date = today.plusDays(offset)
                        val completed = date <= today && date in completedDates
                        val restDay = !completed && date !in scheduledDates
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
            }
        }
    }
}

@Composable
private fun AlarmReadinessCard(
    cameraReady: Boolean,
    notificationsReady: Boolean,
    fullScreenReady: Boolean,
    exactAlarmsReady: Boolean,
    onFixCamera: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixFullScreen: () -> Unit,
    onFixExactAlarms: () -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
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
@OptIn(ExperimentalFoundationApi::class)
private fun AlarmSummaryCard(
    modifier: Modifier,
    alarm: AlarmConfig,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
            .border(if (selected) 2.dp else 0.dp, if (selected) Forest else Color.Transparent, RoundedCornerShape(34.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
