package com.lurremcfly.yogaalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.audio.AlarmAudio
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmSound
import com.lurremcfly.yogaalarm.model.PoseStep
import com.lurremcfly.yogaalarm.model.ProPlan
import com.lurremcfly.yogaalarm.model.YogaPose
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private val Ink = Color(0xFF142018)
private val MutedInk = Color(0xFF647069)
private val Lime = Color(0xFFBDEC68)
private val Forest = Color(0xFF1E3A28)
private val SoftSurface = Color(0xFFF8FBF7)
private val Hairline = Color(0xFFDDE4DE)

@Composable
fun AlarmHomeScreen(
    alarms: List<AlarmConfig>,
    isPro: Boolean,
    onOpenPro: () -> Unit,
    onOpenPrivacy: () -> Unit,
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactHeader = maxWidth < 340.dp
                    val headerFontSize = when {
                        maxWidth < 300.dp -> 24.sp
                        compactHeader -> 30.sp
                        else -> 38.sp
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Your alarms",
                                color = Ink,
                                fontSize = headerFontSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(if (compactHeader) 8.dp else 16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (compactHeader) 8.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = if (isPro) {
                                    Modifier
                                        .size(if (compactHeader) 46.dp else 50.dp)
                                        .clickable(onClick = onOpenPro)
                                } else {
                                    Modifier.clickable(onClick = onOpenPro)
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (isPro) Lime else Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isPro) Lime else Hairline),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        if (isPro) "PRO" else "TRY PRO",
                                        modifier = if (isPro) Modifier else Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                        color = Forest,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(if (compactHeader) 52.dp else 56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.dp, Hairline, CircleShape)
                                    .clickable(onClick = onAddAlarm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Light)
                            }
                        }
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
                        selected = alarm.id == selectedAlarmId,
                        onClick = { onEditAlarm(alarm.id) },
                        onLongClick = { selectedAlarmId = alarm.id },
                        onToggle = { onToggleAlarm(alarm.id, it) },
                    )
                }
            }

        }
        TextButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFEAF4FF)),
            onClick = onOpenPrivacy,
        ) {
            Text("Privacy & safety", color = MutedInk, fontSize = 13.sp)
        }
        alarms.firstOrNull { it.id == selectedAlarmId }?.let { alarm ->
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { selectedAlarmId = null },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 64.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyInfoSheet(
    onDismiss: () -> Unit,
    onReadPolicy: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text("Privacy & safety", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text("Your camera stays private", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Pose detection runs on your device. Camera frames are never recorded, saved, or uploaded.",
                color = MutedInk,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text("Movement, not medical advice", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Yoga Alarm is a movement alarm, not a medical device. It does not diagnose, treat, cure, or prevent any condition.",
                color = MutedInk,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                onClick = onReadPolicy,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
            ) {
                Text("Read privacy policy", fontWeight = FontWeight.Bold)
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
@OptIn(ExperimentalFoundationApi::class)
private fun AlarmSummaryCard(
    alarm: AlarmConfig,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(34.dp))
            .background(Color.White)
            .border(if (selected) 2.dp else 0.dp, if (selected) Forest else Color.Transparent, RoundedCornerShape(34.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorScreen(
    initialAlarm: AlarmConfig,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit,
    onSave: (AlarmConfig) -> Unit,
    onTestRoutine: (AlarmConfig) -> Unit,
    onTestPose: (AlarmConfig, PoseStep) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var draft by rememberSaveable(initialAlarm.id) { mutableStateOf(initialAlarm) }
    var durationSheetSlot by remember { mutableStateOf<Int?>(null) }
    var soundSheetOpen by remember { mutableStateOf(false) }
    var snoozeSheetOpen by remember { mutableStateOf(false) }
    val editorScrollState = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF3F5F3), Color(0xFFF1FBF5), Color(0xFFF3EEFF)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars),
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
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onTestRoutine(draft) }
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
                            enabled = !isPro || draft.routine.size < 10,
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
                onSave(draft.copy(name = draft.name.trim().ifEmpty { "Morning movement" }))
            },
        )
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
            isPro = isPro,
            onUpgrade = {
                soundSheetOpen = false
                onUpgrade()
            },
            onDismiss = { soundSheetOpen = false },
            onSelect = { sound ->
                draft = draft.copy(sound = sound, soundEnabled = true)
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
) {
    val fontScale = LocalDensity.current.fontScale
    val poses = YogaPose.entries
    val pagerState = rememberPagerState(
        initialPage = poses.indexOf(step.pose).coerceAtLeast(0),
        pageCount = { poses.size },
    )
    LaunchedEffect(pagerState, isPro) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            poses.getOrNull(page)?.takeIf { (isPro || it.isFree) && it != step.pose }?.let(onSelectPose)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Lime else Hairline),
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
                                        modifier = Modifier.clickable(onClick = onSelectDuration),
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
                                        modifier = Modifier.clickable(onClick = onTryPose),
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
internal fun PoseGlyph(pose: YogaPose, modifier: Modifier = Modifier) {
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
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (AlarmSound) -> Unit,
) {
    val context = LocalContext.current
    var previewPlayer by remember { mutableStateOf<AlarmAudio?>(null) }
    var previewing by remember { mutableStateOf<AlarmSound?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(previewing) {
        if (previewing != null) {
            delay(10_000)
            previewPlayer?.close()
            previewPlayer = null
            previewing = null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        previewPlayer?.close()
        previewPlayer = null
        previewing = null
    }
    DisposableEffect(Unit) {
        onDispose { previewPlayer?.close() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Alarm sound", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(if (isPro) "Choose your alarm sound." else "Upgrade to Pro to unlock the dimmed sounds.", color = MutedInk, fontSize = 13.sp)
            previewError?.let { Text(it, color = Color(0xFFB4232C)) }
            Spacer(Modifier.height(18.dp))
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(AlarmSound.entries, key = { it.name }) { sound ->
                    val locked = !sound.isFree && !isPro
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (locked) 0.45f else 1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (sound == selectedSound) Color(0xFFF5FFE6) else SoftSurface)
                            .border(1.dp, if (sound == selectedSound) Lime else Hairline, RoundedCornerShape(22.dp))
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
                                previewPlayer?.close()
                                previewPlayer = null
                                previewError = null
                                if (previewing == sound) {
                                    previewing = null
                                } else {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallSheet(
    activePlan: ProPlan?,
    prices: Map<ProPlan, String>,
    billingReady: Boolean,
    purchaseInProgress: Boolean,
    billingMessage: String?,
    onDismiss: () -> Unit,
    onPurchase: (ProPlan) -> Unit,
    onRestorePurchases: () -> Unit,
    onManageSubscription: () -> Unit,
    onActivateTestPlan: (ProPlan) -> Unit,
) {
    var selectedPlan by remember(activePlan) { mutableStateOf(activePlan ?: ProPlan.YEARLY) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoftSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp),
        ) {
            Text("Make every morning yours", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Unlock all poses and sounds, choose 1–10 poses, and set holds from 10–60 seconds.",
                color = MutedInk,
                fontSize = 15.sp,
            )
            if (activePlan != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(999.dp), color = Lime.copy(alpha = 0.55f)) {
                    Text(
                        buildString {
                            append("PRO ACTIVE · ${activePlan.displayName.uppercase(Locale.US)}")
                            if (BuildConfig.DEBUG) append(" TEST")
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            ProPlan.entries.forEach { plan ->
                val selected = selectedPlan == plan
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlan = plan },
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) Color(0xFFF1FFD9) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Lime else Hairline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plan.displayName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                if (plan != ProPlan.MONTHLY) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (plan == ProPlan.YEARLY) "SAVE 79%" else "BEST VALUE",
                                        color = Forest,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(plan.billingPeriod, color = MutedInk, fontSize = 13.sp)
                        }
                        Text(
                            prices[plan] ?: plan.fallbackPrice,
                            color = Ink,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = {
                    if (BuildConfig.DEBUG) onActivateTestPlan(selectedPlan) else onPurchase(selectedPlan)
                },
                enabled = if (BuildConfig.DEBUG) {
                    true
                } else {
                    activePlan == null && billingReady && prices.containsKey(selectedPlan) && !purchaseInProgress
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    when {
                        purchaseInProgress -> "Opening Google Play…"
                        BuildConfig.DEBUG && activePlan == null -> "Activate test Pro"
                        activePlan != null && !BuildConfig.DEBUG -> "Pro is active"
                        activePlan == null -> "Upgrade to Pro"
                        else -> "Choose ${selectedPlan.displayName}"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRestorePurchases) {
                    Text("Restore purchases", color = Forest, fontWeight = FontWeight.SemiBold)
                }
                if (activePlan != null && activePlan != ProPlan.LIFETIME && !BuildConfig.DEBUG) {
                    TextButton(onClick = onManageSubscription) {
                        Text("Manage subscription", color = Forest, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            billingMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth(),
                    color = MutedInk,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                if (BuildConfig.DEBUG) {
                    "Test upgrade only · No charge in this debug APK"
                } else if (!billingReady) {
                    "Connecting to Google Play…"
                } else {
                    "Payment is handled securely by Google Play. Subscriptions renew until cancelled."
                },
                modifier = Modifier.fillMaxWidth(),
                color = MutedInk,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EditorActions(onCancel: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
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
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val durations = listOf(10, 15, 20, 30, 45, 60)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoftSurface) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
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
