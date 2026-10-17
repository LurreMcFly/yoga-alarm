package com.lurremcfly.yogaalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.alarm.AlarmScheduler
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.YogaPose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class OnboardingStep {
    Permissions, Welcome, Add, Time, Name, Days, Sound, SoundChoice, Routine,
    Pose1, Pose2, Pose3, Placement, Save, Complete;

    val isPose: Boolean get() = this == Pose1 || this == Pose2 || this == Pose3
    val isPage: Boolean get() = this == Permissions || this == Welcome || this == Routine || this == Placement || this == Complete
    val tapControl: Boolean get() = this == Add || this == Sound || this == Save
    fun next() = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
    fun previous() = entries[(ordinal - 1).coerceAtLeast(0)]
}

internal class OnboardingTargets {
    val bounds = mutableStateMapOf<OnboardingStep, Rect>()
    var editorBounds by mutableStateOf(Rect.Zero)
    var readinessBounds by mutableStateOf(Rect.Zero)
    var tipHeight by mutableFloatStateOf(0f)
}

internal fun Modifier.onboardingTarget(step: OnboardingStep, targets: OnboardingTargets?): Modifier =
    if (targets == null) this else onGloballyPositioned {
        targets.bounds[step] = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }

@Composable
internal fun OnboardingOverlay(
    step: OnboardingStep,
    targets: OnboardingTargets,
    alarm: AlarmConfig?,
    practised: Boolean = false,
    alarmReady: Boolean = true,
    onStep: (OnboardingStep) -> Unit,
    onSkip: () -> Unit,
    onPractice: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    showRoutineIntroduction: Boolean = true,
    permissionContent: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var tipHeight by remember(step) { mutableFloatStateOf(0f) }
    val cream = Color(0xFFFCFAF6)
    val reveal = remember(step) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var transitioning by remember(step) { mutableStateOf(false) }
    LaunchedEffect(step) { reveal.animateTo(1f, tween(900)) }
    fun transitionTo(next: OnboardingStep) {
        if (transitioning) return
        transitioning = true
        scope.launch {
            reveal.animateTo(0f, tween(350))
            onStep(next)
            transitioning = false
        }
    }
    BackHandler { if (step == OnboardingStep.Permissions || step == OnboardingStep.Welcome || step == OnboardingStep.Complete) onSkip() else transitionTo(step.previous()) }
    val finger = remember(step) { Animatable(0f) }
    LaunchedEffect(step) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        while (true) {
            if (step.isPose) {
                finger.snapTo(80f)
                finger.animateTo(-80f, tween(1_500))
                delay(350)
            } else {
                finger.animateTo(-5f, tween(550))
                finger.animateTo(5f, tween(550))
            }
        }
    }
    BoxWithConstraints(
        modifier.fillMaxSize().graphicsLayer { alpha = reveal.value }
            .onGloballyPositioned { origin = it.positionInWindow() }
            .semantics { paneTitle = "How to use Yoga Alarm" },
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val topInset = WindowInsets.statusBars.getTop(density).toFloat()
        val bottomInset = max(WindowInsets.navigationBars.getBottom(density), WindowInsets.ime.getBottom(density)).toFloat()
        val gap = with(density) { 16.dp.toPx() }
        val target = targets.bounds[step]
        val wide = step == OnboardingStep.Name || step == OnboardingStep.Days || step == OnboardingStep.Sound
        val card = targets.editorBounds.takeIf { wide && it.width > 0f }
        val hole = target?.let {
            Rect(
                left = ((card?.left?.plus(with(density) { 2.dp.toPx() }) ?: it.left) - origin.x).coerceIn(0f, width),
                top = (it.top - origin.y - if (card != null) with(density) { 6.dp.toPx() } else 0f).coerceIn(0f, height),
                right = ((card?.right?.minus(with(density) { 2.dp.toPx() }) ?: it.right) - origin.x).coerceIn(0f, width),
                bottom = (it.bottom - origin.y + if (card != null) with(density) { 6.dp.toPx() } else 0f).coerceIn(0f, height),
            )
        }?.takeIf { it.width > 0f && it.height > 0f }
        if (step.isPage && step != OnboardingStep.Routine) {
            Box(Modifier.fillMaxSize().background(cream))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    if (!step.isPage && hole != null) addRoundRect(RoundRect(hole, CornerRadius(minOf(28.dp.toPx(), hole.height / 2f))))
                }
                drawPath(path, Color(0xAA0D1913))
                if (!step.isPage && hole != null) drawRoundRect(
                    AlarmLime, hole.topLeft, hole.size, CornerRadius(minOf(28.dp.toPx(), hole.height / 2f)), style = Stroke(2.dp.toPx()),
                )
            }
        }
        // Only the highlighted control receives pointer input through the guide.
        val blockers = if (step.isPage || hole == null) listOf(Rect(0f, 0f, width, height)) else listOf(
            Rect(0f, 0f, width, hole.top), Rect(0f, hole.top, hole.left, hole.bottom),
            Rect(hole.right, hole.top, width, hole.bottom), Rect(0f, hole.bottom, width, height),
        )
        blockers.forEach { rect ->
            Box(Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                })
        }

        if (step.isPage) {
            if (step == OnboardingStep.Routine) {
                AnimatedVisibility(showRoutineIntroduction, Modifier.align(Alignment.Center),
                    enter = fadeIn(tween(900)) + slideInVertically(tween(900)) { it / 16 },
                    exit = fadeOut(tween(350))) {
                    Surface(Modifier.padding(horizontal = 20.dp, vertical = 80.dp)
                        .widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cream) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                            Text("Build your yoga routine", fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, color = Forest)
                            Text("Choose the poses you’ll do when your alarm rings.", Modifier.padding(top = 12.dp, bottom = 20.dp), color = MutedInk, fontSize = 14.sp)
                            RoutineOverview(alarm)
                            GuideButton("Choose poses", Modifier.padding(top = 20.dp)) { transitionTo(OnboardingStep.Pose1) }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 88.dp, bottom = 28.dp)) {
                    when (step) {
                        OnboardingStep.Permissions -> {
                            Text("A few permissions first", color = Forest, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                            Text("So your alarm rings and the camera can check your poses.", Modifier.padding(top = 12.dp, bottom = 24.dp), color = MutedInk, fontSize = 14.sp)
                            permissionContent?.invoke()
                            if (alarmReady) GuideButton("Continue", Modifier.padding(top = 24.dp)) { transitionTo(OnboardingStep.Welcome) }
                            else TextButton(onClick = { transitionTo(OnboardingStep.Welcome) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Text("Set up later", color = Forest)
                            }
                        }
                        OnboardingStep.Welcome -> {
                            Text("YOGA ALARM", color = MutedInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("An alarm that\ngets you moving.", Modifier.padding(top = 14.dp), color = Forest, fontSize = 36.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold)
                            PoseGlyph(YogaPose.MOUNTAIN, Modifier.fillMaxWidth().height(190.dp).padding(vertical = 20.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("Set an alarm", "Do your poses", "Alarm stops").forEachIndexed { index, text ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${index + 1}", color = Forest, fontWeight = FontWeight.Bold)
                                        Text(text, Modifier.padding(top = 8.dp), color = MutedInk, fontSize = 11.sp)
                                    }
                                }
                            }
                            Text("Use the camera to follow your poses and stop the alarm.", Modifier.padding(vertical = 24.dp), color = MutedInk, fontSize = 14.sp)
                            GuideButton("Set up an alarm") { transitionTo(OnboardingStep.Add) }
                        }
                        OnboardingStep.Placement -> {
                            Text(if (practised) "You’ve got it." else "Try your first pose.", color = Forest, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (practised) "Practice complete." else "Phone upright. Screen facing you.", Modifier.padding(vertical = 20.dp), color = MutedInk, fontSize = 14.sp)
                            Image(painterResource(R.drawable.onboarding_phone_setup), "Place your phone upright, take about two steps back, then follow the pose.", Modifier.fillMaxWidth())
                            Text("5 seconds · sound off", Modifier.padding(top = 24.dp, bottom = 16.dp), color = Forest, fontWeight = FontWeight.SemiBold)
                            Text("Processed on your device. Never recorded.", color = MutedInk, fontSize = 12.sp)
                            GuideButton(if (practised) "Next" else "Try it now", Modifier.padding(top = 24.dp)) {
                                if (practised) transitionTo(OnboardingStep.Save) else onPractice()
                            }
                            TextButton(onClick = { if (practised) onPractice() else transitionTo(OnboardingStep.Save) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (practised) "Practise again" else "Skip practice", color = Forest)
                            }
                        }
                        OnboardingStep.Complete -> if (alarm != null) {
                            Text(if (alarm.enabled && alarmReady) "Your alarm is ready." else "Your alarm is saved.", color = Forest, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                            Text(alarm.name, Modifier.padding(vertical = 18.dp), color = MutedInk, fontSize = 18.sp)
                            Text(if (alarm.enabled && alarmReady) AlarmScheduler.nextOccurrence(alarm).format(DateTimeFormatter.ofPattern("EEEE HH:mm")) else "Finish alarm setup on the home screen.", color = Forest, fontSize = 22.sp)
                            Spacer(Modifier.height(24.dp))
                            alarm.routine.forEachIndexed { index, pose ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${index + 1}. ${pose.pose.displayName}", color = Forest)
                                    Text("${pose.durationSeconds} sec", color = MutedInk)
                                }
                            }
                            Text("${alarm.routine.sumOf { it.durationSeconds }} seconds of movement.", Modifier.padding(vertical = 24.dp), color = MutedInk)
                            GuideButton("Done", onClick = onSkip)
                            TextButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text("Test full routine", color = Forest) }
                        }
                        else -> Unit
                    }
                }
            }
        } else {
            if (hole != null && step != OnboardingStep.SoundChoice) {
                val downward = step == OnboardingStep.Add || step == OnboardingStep.Save
                Text(if (downward) "👇" else "👆", fontSize = 54.sp, modifier = Modifier
                    .offset { IntOffset(
                        (hole.center.x - with(density) { 27.dp.toPx() } + if (step.isPose) with(density) { finger.value.dp.toPx() } else 0f).roundToInt(),
                        ((if (downward) hole.top - with(density) { 68.dp.toPx() } else hole.bottom + with(density) { 4.dp.toPx() }) + if (step.isPose) 0f else with(density) { finger.value.dp.toPx() }).roundToInt(),
                    ) }.clearAndSetSemantics {})
            }
            val topTip = step == OnboardingStep.Add || step == OnboardingStep.Save
            val tipTop = if (topTip) topInset + with(density) { 78.dp.toPx() } else (height - bottomInset - tipHeight - gap).coerceAtLeast(topInset + with(density) { 76.dp.toPx() })
            Surface(Modifier.offset { IntOffset(gap.roundToInt(), tipTop.roundToInt()) }
                .graphicsLayer { translationY = (1f - reveal.value) * 12.dp.toPx() }
                .width(with(density) { (width - gap * 2).toDp() })
                .heightIn(max = with(density) { ((height - topInset - bottomInset) * 0.48f).toDp() })
                .onSizeChanged { tipHeight = it.height.toFloat(); targets.tipHeight = tipHeight }
                .semantics { liveRegion = LiveRegionMode.Polite }, shape = RoundedCornerShape(24.dp), color = cream) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    val (title, detail) = when (step) {
                        OnboardingStep.Add -> "Add an alarm" to "Tap Add alarm."
                        OnboardingStep.Time -> "Set the time" to "Scroll to choose."
                        OnboardingStep.Name -> "Name your alarm" to "Pick a name you’ll recognise."
                        OnboardingStep.Days -> "Choose repeat days" to "Green = selected. No days = once."
                        OnboardingStep.Sound -> "Choose your alarm sound" to "Tap Sound."
                        OnboardingStep.SoundChoice -> "Find your sound" to "Preview, then choose."
                        OnboardingStep.Pose1 -> "Choose pose 1" to "Swipe to explore your three free poses."
                        OnboardingStep.Pose2 -> "Choose pose 2" to "Each pose lasts 20 seconds in Free."
                        OnboardingStep.Pose3 -> "Choose pose 3" to "Your routine plays from top to bottom."
                        else -> "Save your alarm" to "Keep your time, name and poses."
                    }
                    Text(title, fontSize = 24.sp, lineHeight = 27.sp, color = Forest, fontWeight = FontWeight.SemiBold)
                    Text(detail, Modifier.padding(top = 12.dp, bottom = if (step.tapControl) 0.dp else 18.dp), color = MutedInk, fontSize = 14.sp)
                    if (!step.tapControl) GuideButton(if (step == OnboardingStep.Pose3) "Try a pose" else "Next") { transitionTo(step.next()) }
                }
            }
        }
        if (step != OnboardingStep.Complete) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = if (step.isPage && step != OnboardingStep.Routine) cream else Forest) {
                    TextButton(onClick = { if (step == OnboardingStep.Permissions || step == OnboardingStep.Welcome) onSkip() else transitionTo(step.previous()) }) {
                        Text(if (step == OnboardingStep.Permissions || step == OnboardingStep.Welcome) "Close" else "← Back", color = if (step.isPage && step != OnboardingStep.Routine) Forest else Color.White, fontSize = 12.sp)
                    }
                }
                Text(if (step == OnboardingStep.Permissions) "Getting ready" else "${step.ordinal} / ${OnboardingStep.entries.size - 1}", color = if (step.isPage && step != OnboardingStep.Routine) Forest else Color.White, fontSize = 11.sp)
                Surface(shape = RoundedCornerShape(50), color = if (step.isPage && step != OnboardingStep.Routine) cream else Forest) {
                    TextButton(onClick = onSkip) { Text("Skip tour", color = if (step.isPage && step != OnboardingStep.Routine) Forest else Color.White, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
internal fun OnboardingInvitation(cardBounds: Rect, onStart: () -> Unit, onLater: () -> Unit) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            Box(Modifier.fillMaxSize().pointerInput(onLater) { detectTapGestures { onLater() } })
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val top = WindowInsets.statusBars.getTop(density).toFloat()
            val bottom = height - WindowInsets.navigationBars.getBottom(density)
            val placement = if (cardBounds.width > 0f && cardBounds.height > 0f) {
                val cardWidth = cardBounds.width.coerceAtMost(width)
                val cardHeight = cardBounds.height.coerceAtMost((bottom - top).coerceAtLeast(1f))
                Modifier.offset { IntOffset(
                    (cardBounds.left - origin.x).coerceIn(0f, width - cardWidth).roundToInt(),
                    (cardBounds.top - origin.y).coerceIn(top, bottom - cardHeight).roundToInt(),
                ) }.width(with(density) { cardWidth.toDp() }).height(with(density) { cardHeight.toDp() })
            } else {
                Modifier.align(Alignment.Center).padding(horizontal = 22.dp).widthIn(max = 440.dp).fillMaxWidth()
                    .heightIn(max = with(density) { (bottom - top).toDp() })
            }
            Surface(placement, shape = RoundedCornerShape(28.dp), color = Color(0xFFFCFAF6)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
                    Text("YOGA ALARM", color = MutedInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    PoseGlyph(YogaPose.MOUNTAIN, Modifier.fillMaxWidth().height(110.dp).padding(vertical = 12.dp))
                    Text("Take a quick tour?", color = Forest, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                    Text("Set an alarm and build your yoga routine.", Modifier.padding(top = 12.dp, bottom = 24.dp), color = MutedInk, fontSize = 14.sp)
                    GuideButton("Start tour", onClick = onStart)
                    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("Later", color = Forest) }
                }
            }
        }
    }
}

@Composable
private fun RoutineOverview(alarm: AlarmConfig?) {
    val poses = alarm?.routine ?: return
    if (poses.isEmpty()) return
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var reducedMotion by remember { mutableStateOf(false) }
    LaunchedEffect(poses, density) {
        reducedMotion = coroutineContext[MotionDurationScale]?.scaleFactor == 0f
        if (reducedMotion) return@LaunchedEffect
        val distance = with(density) { (180.dp.roundToPx() + 12.dp.roundToPx()).toFloat() } * poses.size
        while (true) {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                reducedMotion = true
                return@LaunchedEffect
            }
            listState.scroll {
                var previous = 0f
                animate(0f, distance, animationSpec = tween(poses.size * 6_000, easing = LinearEasing)) { value, _ ->
                    scrollBy(value - previous)
                    previous = value
                }
            }
            // The final card repeats the first, so resetting keeps the loop seamless.
            listState.scrollToItem(0)
        }
    }
    if (reducedMotion) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            poses.forEachIndexed { index, pose -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pose ${index + 1}", color = MutedInk, fontSize = 11.sp)
                PoseGlyph(pose.pose, Modifier.fillMaxWidth().height(80.dp))
                Text(pose.pose.displayName, color = Forest, fontSize = 12.sp)
            } }
        }
    } else {
        LazyColumn(state = listState, userScrollEnabled = false, modifier = Modifier.fillMaxWidth().height(200.dp)
            .background(Color(0xFFEDF3E8), RoundedCornerShape(22.dp)), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(poses + poses.first()) { index, pose ->
                Surface(Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pose ${index % poses.size + 1} of ${poses.size}", Modifier.align(Alignment.Start), color = MutedInk, fontSize = 10.sp)
                        PoseGlyph(pose.pose, Modifier.fillMaxWidth().weight(1f))
                        Text(pose.pose.displayName, color = Forest, fontWeight = FontWeight.SemiBold)
                        Text("${pose.durationSeconds} sec", color = MutedInk, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 51.dp),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White)) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun OnboardingPracticeComplete(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF07100A)).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nice work! ✓", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Text("Practice complete.", Modifier.padding(vertical = 24.dp), color = Color.White.copy(alpha = 0.75f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 51.dp),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = AlarmLime, contentColor = Forest)) {
            Text("Continue setup", fontWeight = FontWeight.SemiBold)
        }
    }
}
