package com.yogaalarm.prototype.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yogaalarm.prototype.R
import com.yogaalarm.prototype.audio.PrototypeAlarmAudio
import com.yogaalarm.prototype.model.AlarmConfig
import com.yogaalarm.prototype.model.AlarmSound
import com.yogaalarm.prototype.model.PoseStep
import com.yogaalarm.prototype.model.YogaPose
import java.util.Locale
import kotlinx.coroutines.launch

private val Ink = Color(0xFF142018)
private val MutedInk = Color(0xFF647069)
private val Lime = Color(0xFFBDEC68)
private val Forest = Color(0xFF1E3A28)
private val SoftSurface = Color(0xFFF8FBF7)
private val Hairline = Color(0xFFDDE4DE)

@Composable
fun AlarmHomeScreen(
    alarms: List<AlarmConfig>,
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
) {
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 22.dp,
                top = 56.dp,
                end = 22.dp,
                bottom = 110.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Your alarms", color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Wake up moving.", color = MutedInk, fontSize = 17.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Hairline, CircleShape)
                            .clickable(onClick = onAddAlarm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    }
                }
                Spacer(Modifier.height(24.dp))
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
                    AlarmSummaryCard(
                        alarm = alarm,
                        onClick = { onEditAlarm(alarm.id) },
                        onToggle = { onToggleAlarm(alarm.id, it) },
                    )
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
    val readyCount = listOf(cameraReady, notificationsReady, fullScreenReady, exactAlarmsReady).count { it }
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
            if (!cameraReady) {
                ReadinessRow("Camera", "Verifies your poses", "Allow", onFixCamera)
            }
            if (!notificationsReady) {
                ReadinessRow("Notifications", "Shows a ringing alarm", "Allow", onFixNotifications)
            }
            if (!fullScreenReady) {
                ReadinessRow("Full-screen alarms", "Opens over your lock screen", "Enable", onFixFullScreen)
            }
            if (!exactAlarmsReady) {
                ReadinessRow("Exact alarms", "Keeps alarms on time", "Enable", onFixExactAlarms)
            }
        }
    }
}

@Composable
private fun ReadinessRow(
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
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
            Surface(shape = RoundedCornerShape(999.dp), color = Lime) {
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
    alarm: AlarmConfig,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(34.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = alarm.name,
                        color = if (alarm.enabled) Ink else MutedInk.copy(alpha = 0.55f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute),
                        color = if (alarm.enabled) Ink else MutedInk.copy(alpha = 0.45f),
                        fontSize = 54.sp,
                        lineHeight = 60.sp,
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
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Hairline)
            Spacer(Modifier.height(16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    initialAlarm: AlarmConfig,
    onCancel: () -> Unit,
    onSave: (AlarmConfig) -> Unit,
    onTestRoutine: (AlarmConfig) -> Unit,
    onTestPose: (AlarmConfig, PoseStep) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var draft by remember(initialAlarm.id) { mutableStateOf(initialAlarm) }
    var durationSheetSlot by remember { mutableStateOf<Int?>(null) }
    var soundSheetOpen by remember { mutableStateOf(false) }
    var snoozeSheetOpen by remember { mutableStateOf(false) }
    var previewProDurations by rememberSaveable { mutableStateOf(false) }
    val editorScrollState = rememberScrollState()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            EditorActions(
                onCancel = onCancel,
                onSave = {
                    onSave(draft.copy(name = draft.name.trim().ifEmpty { "Morning movement" }))
                },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF3F5F3), Color(0xFFF1FBF5), Color(0xFFF3EEFF)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(editorScrollState)
                .padding(bottom = 22.dp),
        ) {
            AlarmTimePicker(
                hour = draft.hour,
                minute = draft.minute,
                onHourChange = { draft = draft.copy(hour = it) },
                onMinuteChange = { draft = draft.copy(minute = it) },
                modifier = Modifier.graphicsLayer {
                    translationY = editorScrollState.value.toFloat()
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 22.dp),
            ) {
                    TextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it.take(40)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Alarm name") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Forest,
                            unfocusedIndicatorColor = Hairline,
                        ),
                    )
                    Spacer(Modifier.height(18.dp))
                    WeekdayPicker(
                        selectedDays = draft.weekdays,
                        onSelectionChange = { draft = draft.copy(weekdays = it) },
                    )
                    Spacer(Modifier.height(30.dp))
                    SettingRow(
                        "Snooze",
                        "${draft.snoozeMinutes} minutes, ${draft.snoozeCount} ${if (draft.snoozeCount == 1) "time" else "times"}",
                        draft.snoozeEnabled,
                        onClick = { snoozeSheetOpen = true },
                    ) {
                        draft = draft.copy(snoozeEnabled = it)
                    }
                    HorizontalDivider(color = Hairline)
                    SettingRow("Sound", draft.sound.displayName, draft.soundEnabled, onClick = { soundSheetOpen = true }) {
                        draft = draft.copy(soundEnabled = it)
                    }
                    HorizontalDivider(color = Hairline)
                    SettingRow("Vibration", "Gentle pulse", draft.vibrationEnabled) {
                        draft = draft.copy(vibrationEnabled = it)
                    }
                    Spacer(Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text("MORNING ROUTINE", color = MutedInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text(
                            "${draft.routine.size} poses · ${draft.routine.sumOf { it.durationSeconds }} seconds",
                            color = MutedInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    draft.routine.forEachIndexed { index, step ->
                        PoseCarouselCard(
                            index = index,
                            step = step,
                            onSelectPose = { pose ->
                                draft = draft.copy(
                                    routine = draft.routine.mapIndexed { slot, candidate ->
                                        if (slot == index) candidate.copy(pose = pose) else candidate
                                    },
                                )
                            },
                            onSelectDuration = { durationSheetSlot = index },
                            onTryPose = { onTestPose(draft, step) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFFF1F6F1),
                            disabledContentColor = MutedInk,
                        ),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("+ Add another pose   PRO", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onTestRoutine(draft) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Test complete routine", modifier = Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
                    }
            }
        }
    }

    durationSheetSlot?.let { slotIndex ->
        DurationPickerSheet(
            selectedDuration = draft.routine[slotIndex].durationSeconds,
            previewProDurations = previewProDurations,
            onPreviewChange = { previewProDurations = it },
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
            onDismiss = { soundSheetOpen = false },
            onSelect = { sound ->
                draft = draft.copy(sound = sound, soundEnabled = true)
                soundSheetOpen = false
            },
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
    onSelectionChange: (Set<Int>) -> Unit,
) {
    val days = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { (day, label) ->
            val selected = day in selectedDays
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clickable {
                        onSelectionChange(if (selected) selectedDays - day else selectedDays + day)
                    },
                shape = CircleShape,
                color = if (selected) Lime else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) Ink else MutedInk, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PoseCarouselCard(
    index: Int,
    step: PoseStep,
    onSelectPose: (YogaPose) -> Unit,
    onSelectDuration: () -> Unit,
    onTryPose: () -> Unit,
) {
    val poses = YogaPose.entries
    val pagerState = rememberPagerState(
        initialPage = poses.indexOf(step.pose).coerceAtLeast(0),
        pageCount = { poses.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            poses.getOrNull(page)?.takeIf { it.isFree && it != step.pose }?.let(onSelectPose)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFF1F6F1),
    ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                pageSpacing = 10.dp,
            ) { page ->
                val pose = poses[page]
                val selected = page == pagerState.currentPage && pose.isFree
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (pose.isFree) 1f else 0.62f),
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) Color(0xFFF5FFE6) else Color.White.copy(alpha = 0.82f),
                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Lime) else null,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (!pose.isFree) "PRO · LOCKED" else if (selected) "SELECTED" else "FREE",
                            color = if (selected) Forest else MutedInk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        PoseGlyph(pose, Modifier.size(132.dp))
                        Text(pose.displayName, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        when {
                            !pose.isFree -> Text("Unlock Pro", color = MutedInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            selected -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    modifier = Modifier.clickable(onClick = onSelectDuration),
                                    shape = RoundedCornerShape(50),
                                    color = Color.White,
                                ) {
                                    Text(
                                        "${step.durationSeconds} sec",
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                        color = Forest,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Surface(
                                    modifier = Modifier.clickable(onClick = onTryPose),
                                    shape = RoundedCornerShape(50),
                                    color = Forest,
                                ) {
                                    Text(
                                        "Try pose",
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            else -> Text("Swipe to select", color = MutedInk, fontSize = 12.sp)
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

@Composable
private fun PoseGlyph(pose: YogaPose, modifier: Modifier = Modifier) {
    val illustration = when (pose) {
        YogaPose.MOUNTAIN -> R.drawable.pose_mountain
        YogaPose.WARRIOR_TWO -> R.drawable.pose_warrior_two
        YogaPose.TREE -> R.drawable.pose_tree
        YogaPose.CHAIR -> R.drawable.pose_chair
        YogaPose.FORWARD_FOLD -> R.drawable.pose_forward_fold
        YogaPose.TRIANGLE -> R.drawable.pose_triangle
        YogaPose.GODDESS -> R.drawable.pose_goddess
        YogaPose.WIDE_LEG_FOLD -> R.drawable.pose_wide_legged_fold
    }
    Image(
        painter = painterResource(illustration),
        contentDescription = "${pose.displayName} pose illustration",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MutedInk, fontSize = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = yogaSwitchColors())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundPickerSheet(
    selectedSound: AlarmSound,
    onDismiss: () -> Unit,
    onSelect: (AlarmSound) -> Unit,
) {
    val context = LocalContext.current
    var previewPlayer by remember { mutableStateOf<PrototypeAlarmAudio?>(null) }
    var previewing by remember { mutableStateOf<AlarmSound?>(null) }
    DisposableEffect(Unit) {
        onDispose { previewPlayer?.close() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Alarm sound", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Choose the sound used for this alarm.", color = MutedInk)
            Spacer(Modifier.height(18.dp))
            AlarmSound.entries.forEach { sound ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(sound) }
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sound.displayName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (sound == AlarmSound.MORNING_BELLS) "Bright and melodic" else "Calm birds and nature", color = MutedInk, fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = {
                            previewPlayer?.close()
                            previewPlayer = PrototypeAlarmAudio(context.applicationContext, sound).also {
                                it.start()
                                it.setLevel(0.7f)
                            }
                            previewing = sound
                        },
                    ) {
                        Text(if (previewing == sound) "Playing" else "Preview", color = Forest)
                    }
                    if (sound == selectedSound) Text("✓", color = Forest, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Hairline)
            }
        }
    }
}

@Composable
private fun EditorActions(onCancel: () -> Unit, onSave: () -> Unit) {
    Box(
        modifier = Modifier
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
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.width(1.dp).height(30.dp).background(Hairline))
                TextButton(onClick = onSave, modifier = Modifier.weight(1f)) {
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
                        color = if (pose == selectedPose) Lime.copy(alpha = 0.5f) else Color.White,
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
                color = if (isSelected) Lime.copy(alpha = 0.55f) else Color.White,
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
    previewProDurations: Boolean,
    onPreviewChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val durations = listOf(10, 15, 20, 30, 45, 60)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoftSurface) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
            Text("Hold duration", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("20 seconds is included in the free routine.", color = MutedInk, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            durations.forEach { duration ->
                val locked = duration != 20 && !previewProDurations
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locked) { onSelect(duration) }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Preview Pro durations", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Tester-only control", color = MutedInk, fontSize = 12.sp)
                }
                Switch(checked = previewProDurations, onCheckedChange = onPreviewChange, colors = yogaSwitchColors())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun yogaSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Forest,
    checkedTrackColor = Lime,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFB9C1BB),
    uncheckedBorderColor = Color.Transparent,
)

private fun weekdaySummary(days: Set<Int>): String = when {
    days == setOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    days.isEmpty() -> "Once"
    else -> listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        .filter { it.first in days }
        .joinToString(", ") { it.second }
}
