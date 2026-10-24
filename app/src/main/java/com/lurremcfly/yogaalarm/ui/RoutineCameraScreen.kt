package com.lurremcfly.yogaalarm.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.lurremcfly.yogaalarm.camera.PoseCameraController
import com.lurremcfly.yogaalarm.model.BodyPoint
import com.lurremcfly.yogaalarm.model.PreviewProjection
import com.lurremcfly.yogaalarm.model.PoseFrame
import com.lurremcfly.yogaalarm.model.YogaPose
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Composable
fun RoutineCameraScreen(
    uiState: RoutineUiState,
    frames: StateFlow<PoseFrame?>,
    progress: StateFlow<RoutineProgress>,
    onPoseFrame: (PoseFrame) -> Unit,
    onCameraError: (String) -> Unit,
    onCameraRetry: () -> Unit,
    showBack: Boolean,
    showStop: Boolean,
    showEmergencyStop: Boolean,
    showTechnicalStop: Boolean,
    completionStreakDays: Int,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onEmergencyStop: () -> Unit,
    onTechnicalStop: () -> Unit,
) {
    var cameraAttempt by remember { mutableStateOf(0) }
    var previewReady by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0) }
    val guidePose = if (uiState.phase == RoutinePhase.TRANSITION) uiState.nextPose ?: uiState.pose else uiState.pose
    val guidePoseIndex = uiState.poseIndex + if (uiState.phase == RoutinePhase.TRANSITION && uiState.nextPose != null) 1 else 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07100A)),
    ) {
        val compactLandscape = maxHeight < 600.dp && maxWidth > maxHeight
        val density = LocalDensity.current
        val topInset = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
        val bottomInset = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
        val glyphSize = if (compactLandscape) 72.dp else 96.dp
        if (uiState.phase == RoutinePhase.COMPLETE) {
            RoutineCompletion(uiState, completionStreakDays, showBack, onBack)
            return@BoxWithConstraints
        }
        if (uiState.phase != RoutinePhase.COMPLETE) {
            key(cameraAttempt) {
                SelfiePreview(onPoseFrame, onCameraError, onPreviewReady = { previewReady = it })
            }
        }

        if (uiState.phase != RoutinePhase.COMPLETE) {
            RoutineOverlays(frames, guidePose, uiState)
        }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(210.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.3f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(300.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))

        Box(Modifier.align(if (compactLandscape) Alignment.TopStart else Alignment.TopCenter)
            .fillMaxWidth(if (compactLandscape) 0.4f else 1f)
            .onSizeChanged { headerHeight = it.height }) {
            if (uiState.started && uiState.phase != RoutinePhase.COMPLETE) {
                val context = LocalContext.current
                val preferences = remember(context) {
                    context.getSharedPreferences("pose_illustrations", Context.MODE_PRIVATE)
                }
                val mirrored by rememberSaveable(guidePose, guidePoseIndex) {
                    mutableStateOf(preferences.getBoolean(guidePose.name, false))
                }
                LaunchedEffect(guidePose, guidePoseIndex) {
                    // Re-saving the same next orientation after recreation does not advance it.
                    preferences.edit().putBoolean(guidePose.name, !mirrored).apply()
                }
                key(guidePose, guidePoseIndex) {
                    val arrival = remember { Animatable(0f) }
                    val density = LocalDensity.current
                    val startTranslation = with(density) {
                        this@BoxWithConstraints.maxHeight.toPx() / 2f - WindowInsets.safeDrawing.getTop(density) - 56.dp.toPx()
                    }
                    LaunchedEffect(Unit) {
                        delay(1_000)
                        arrival.animateTo(1f, tween(durationMillis = 650))
                    }
                    PoseGlyph(
                        guidePose,
                        Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                            .padding(top = if (compactLandscape) 56.dp else 8.dp)
                            .size(glyphSize)
                            .graphicsLayer {
                                val remaining = 1f - arrival.value
                                val scale = 1f + remaining * 2.75f
                                scaleX = if (mirrored) -scale else scale
                                scaleY = scale
                                translationY = remaining * startTranslation
                            },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                if (showBack || showStop) {
                    TextButton(onClick = if (showBack) onBack else onStop, modifier = Modifier.align(Alignment.TopStart)) {
                        Text(
                            if (showBack) "‹ Alarm" else "Stop",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(Modifier.align(Alignment.TopCenter).padding(top = if (compactLandscape) 48.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.size(glyphSize))
                    Text(guidePose.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(if (compactLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .fillMaxWidth(if (compactLandscape) 0.6f else 1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(
                    if (compactLandscape) WindowInsetsSides.Vertical else WindowInsetsSides.Bottom,
                ))
                .heightIn(max = (maxHeight - bottomInset - if (compactLandscape) topInset else with(density) {
                    headerHeight.toDp() + 16.dp
                }).coerceAtLeast(48.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compactLandscape) 16.dp else 28.dp, vertical = if (compactLandscape) 8.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState.phase) {
                RoutinePhase.HOLDING, RoutinePhase.PAUSED, RoutinePhase.TRANSITION -> {
                    key(uiState.poseIndex) {
                        RoutineCountdown(progress, uiState.remainingSeconds, uiState.phase)
                    }
                    Spacer(Modifier.height(12.dp))
                    AnimatedContent(
                        targetState = uiState.phase,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "Routine feedback",
                    ) { phase ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when (phase) {
                                    RoutinePhase.PAUSED -> "↶ Return to pose"
                                    RoutinePhase.TRANSITION -> "Next: ${uiState.nextPose?.displayName ?: uiState.pose.displayName}"
                                    else -> "Hold"
                                },
                                color = Color.White, fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                when (phase) {
                                    RoutinePhase.PAUSED -> "${uiState.remainingSeconds} sec remaining · your time is saved"
                                    RoutinePhase.TRANSITION -> "${poseInstruction(uiState.nextPose ?: uiState.pose)}\nStarts in ${uiState.transitionSeconds}"
                                    else -> "Stay with it while the alarm fades"
                                },
                                color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            if (phase == RoutinePhase.PAUSED && (!uiState.framed || uiState.error != null)) {
                                Spacer(Modifier.height(6.dp))
                                Text(uiState.error ?: uiState.framingHint, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                }
                else -> {
                    Text(statusTitle(uiState, previewReady), color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(7.dp))
                    Text(statusDetail(uiState), color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }

            if (uiState.error != null) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        previewReady = false
                        onCameraRetry()
                        cameraAttempt += 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color(0xFF173421)),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        "Try camera again",
                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (showTechnicalStop) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onTechnicalStop) {
                        Text("Stop alarm", color = Color.White.copy(alpha = 0.75f))
                    }
                }
            } else if (showEmergencyStop) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onEmergencyStop) {
                    Text("Emergency stop", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }

    }
}

@Composable
private fun SelfiePreview(onPoseFrame: (PoseFrame) -> Unit, onCameraError: (String) -> Unit, onPreviewReady: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { PoseCameraController(context, onPoseFrame, onCameraError) }
    val preview = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // The shared CameraX viewport also supplies the overlay crop.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        var rotation = preview.display?.rotation
        val bindCamera = Runnable { controller.bind(lifecycleOwner, preview) }
        val observer = Observer<PreviewView.StreamState> { onPreviewReady(it == PreviewView.StreamState.STREAMING) }
        preview.previewStreamState.observe(lifecycleOwner, observer)
        val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right > left && bottom > top &&
                (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
                rotation = preview.display?.rotation
                preview.removeCallbacks(bindCamera)
                preview.postDelayed(bindCamera, 150L)
            }
        }
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val display = preview.display ?: return
                if (display.displayId == displayId && rotation != display.rotation) {
                    rotation = display.rotation
                    preview.removeCallbacks(bindCamera)
                    if (preview.isLaidOut) preview.postDelayed(bindCamera, 150L)
                }
            }
        }
        preview.addOnLayoutChangeListener(layoutListener)
        displayManager.registerDisplayListener(displayListener, null)
        if (preview.isLaidOut && preview.width > 0 && preview.height > 0) controller.bind(lifecycleOwner, preview)
        onDispose {
            preview.removeCallbacks(bindCamera)
            preview.removeOnLayoutChangeListener(layoutListener)
            displayManager.unregisterDisplayListener(displayListener)
            preview.previewStreamState.removeObserver(observer)
            controller.close()
        }
    }
    AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun RoutineOverlays(frames: StateFlow<PoseFrame?>, pose: YogaPose, state: RoutineUiState) {
    val frame by frames.collectAsStateWithLifecycle()
    val locked = state.detected && state.phase != RoutinePhase.TRANSITION
    val targetAlpha by animateFloatAsState(
        targetValue = when {
            locked || !state.cameraReady || state.error != null -> 0f
            state.almostThere && state.phase != RoutinePhase.TRANSITION -> 0.2f
            else -> 1f
        },
        animationSpec = tween(220), label = "Target visibility",
    )
    if (targetAlpha > 0.01f) RoutinePoseGuide(pose, frame, targetAlpha)
    RoutineSkeletonOverlay(frame, locked)
}

@Composable
private fun RoutineCountdown(progress: StateFlow<RoutineProgress>, seconds: Int, phase: RoutinePhase) {
    val value by progress.collectAsStateWithLifecycle()
    val pulse = remember { Animatable(1f) }
    val check = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val ringColor by animateColorAsState(
        if (phase == RoutinePhase.PAUSED) Color.White.copy(alpha = 0.45f) else Lime,
        tween(220), label = "Hold ring color",
    )
    LaunchedEffect(phase) {
        pulse.snapTo(1f)
        if (phase == RoutinePhase.HOLDING || phase == RoutinePhase.TRANSITION) {
            coroutineScope {
                launch {
                    pulse.animateTo(1.07f, tween(160))
                    pulse.animateTo(1f, spring(dampingRatio = 0.6f))
                }
                if (phase == RoutinePhase.TRANSITION) {
                    launch { check.animateTo(1f, spring(dampingRatio = 0.65f)) }
                    launch {
                        glow.snapTo(1f)
                        glow.animateTo(0f, tween(750))
                    }
                }
            }
        }
    }
    Box(
        modifier = Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(176.dp)) {
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = 72.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(Lime.copy(alpha = 0.28f * glow.value), Color.Transparent)))
        }
        CircularProgressIndicator(
            progress = { value.hold },
            modifier = Modifier.size(144.dp),
            color = ringColor,
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeWidth = 2.dp,
            strokeCap = StrokeCap.Round,
        )
        if (phase == RoutinePhase.TRANSITION) {
            Text("✓", color = Lime, fontSize = 64.sp, modifier = Modifier.graphicsLayer {
                alpha = check.value.coerceIn(0f, 1f)
                scaleX = 0.65f + 0.35f * check.value
                scaleY = scaleX
            })
        } else {
            Text(seconds.toString(), color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RoutineCompletion(state: RoutineUiState, streakDays: Int, showBack: Boolean, onBack: () -> Unit) {
    var presented by rememberSaveable(streakDays) { mutableStateOf(false) }
    val sunrise = remember(streakDays) { Animatable(if (presented) 1f else 0f) }
    val entrance = remember(streakDays) { Animatable(if (presented) 1f else 0f) }
    val docking = remember(streakDays) { Animatable(if (presented) 1f else 0f) }
    val bounce = remember(streakDays) { Animatable(1f) }
    var footerSize by remember { mutableStateOf(IntSize.Zero) }
    var numberSize by remember { mutableStateOf(IntSize.Zero) }
    var numberCenter by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(streakDays) {
        if (presented) return@LaunchedEffect
        presented = true
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
            sunrise.snapTo(1f)
            entrance.snapTo(1f)
            docking.snapTo(1f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { entrance.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 180f)) }
            launch { sunrise.animateTo(1f, tween(900)) }
        }
        delay(3_000)
        docking.animateTo(1f, tween(850))
        bounce.animateTo(1.06f, tween(140))
        bounce.animateTo(1f, spring(dampingRatio = 0.6f))
    }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF153523), Color(0xFF07100A))))
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val topPx = WindowInsets.safeDrawing.getTop(density).toFloat() + with(density) { 60.dp.toPx() }
        val availableHeight = (heightPx - footerSize.height - topPx).coerceAtLeast(0f)
        val diameter = minOf(with(density) { 220.dp.toPx() }, widthPx * 0.64f, availableHeight * 0.8f).coerceAtLeast(1f)
        val numberFontSize = with(density) { 16.sp.toPx() }
        val sunCenter = Offset(widthPx / 2f, topPx + availableHeight / 2f)
        val rise = with(density) { 90.dp.toPx() } * (1f - sunrise.value)
        Canvas(Modifier.fillMaxSize()) {
            val center = sunCenter + Offset(0f, rise)
            val radius = diameter / 2f * (0.8f + 0.2f * sunrise.value)
            drawCircle(
                Brush.radialGradient(listOf(Lime.copy(alpha = 0.16f * sunrise.value), Color.Transparent), center, radius * 2f),
                radius * 2f, center,
            )
            drawCircle(
                Brush.verticalGradient(listOf(Color(0xFFF9E4A7), Lime), center.y - radius, center.y + radius),
                radius, center, alpha = sunrise.value,
            )
        }
        if (showBack) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(10.dp)) {
                Text("‹ Alarm", color = Color.White)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { footerSize = it }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .heightIn(max = (maxHeight - with(density) { topPx.toDp() }).coerceAtLeast(48.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(statusTitle(state, true), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { alpha = sunrise.value })
            Spacer(Modifier.height(8.dp))
            Text(statusDetail(state), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
                modifier = Modifier.graphicsLayer { alpha = sunrise.value })
            if (streakDays > 0) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer {
                    scaleX = bounce.value; scaleY = bounce.value
                }) {
                    Text("🔥 ", fontSize = 16.sp, modifier = Modifier.graphicsLayer { alpha = docking.value })
                    Box(Modifier.onGloballyPositioned {
                        numberSize = it.size
                        numberCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                    }) {
                        Text(
                            streakDays.toString(), color = Lime,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer {
                                alpha = if (docking.value == 1f) 1f else 0f
                            },
                        )
                    }
                    Text("-day yoga streak", color = Lime, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer { alpha = docking.value })
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color(0xFF07100A))) {
                Text("Done", modifier = Modifier.padding(horizontal = 36.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
            }
        }
        // Animate outside the scrollable footer so its clip cannot hide the number in the sun.
        if (streakDays > 0 && docking.value < 1f) {
            Text(
                streakDays.toString(), color = lerp(Color(0xFF153523), Lime, docking.value),
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                    val remaining = 1f - docking.value
                    val largeScale = minOf(
                        diameter * 0.72f / numberSize.width.coerceAtLeast(1),
                        diameter * 0.78f / numberFontSize,
                    ).coerceAtLeast(1f)
                    val pop = 0.25f + 0.75f * entrance.value
                    scaleX = 1f + remaining * (largeScale * pop - 1f)
                    scaleY = scaleX
                    translationX = numberCenter.x - origin.x - numberSize.width / 2f +
                        remaining * (origin.x + sunCenter.x - numberCenter.x)
                    translationY = numberCenter.y - origin.y - numberSize.height / 2f +
                        remaining * (origin.y + sunCenter.y + rise - numberCenter.y)
                    alpha = if (numberSize == IntSize.Zero) 0f else entrance.value.coerceIn(0f, 1f)
                },
            )
        }
    }
}

private fun statusTitle(state: RoutineUiState, previewReady: Boolean): String = when {
    state.error != null -> "Camera unavailable"
    state.phase == RoutinePhase.COMPLETE -> when (state.alarmHour) {
        in 5..11 -> "Good morning ☀️"
        in 12..16 -> "Good afternoon ☀️"
        in 17..21 -> "Good evening 🌅"
        else -> "Good night 🌙"
    }
    state.phase == RoutinePhase.TRANSITION -> "Next: ${state.nextPose?.displayName ?: state.pose.displayName}"
    !state.cameraReady && state.phase != RoutinePhase.PAUSED -> if (previewReady) "Preparing pose detection…" else "Starting camera…"
    state.phase == RoutinePhase.HOLDING -> "Hold"
    state.phase == RoutinePhase.PAUSED -> "Return to the pose"
    !state.framed -> "Move into view"
    state.almostThere -> "Almost there"
    else -> "Move into the pose"
}

private fun statusDetail(state: RoutineUiState): String = when {
    state.error != null -> state.error
    state.phase == RoutinePhase.COMPLETE -> "${state.poseCount} poses completed"
    state.phase == RoutinePhase.TRANSITION -> "${poseInstruction(state.nextPose ?: state.pose)}\nStarts in ${state.transitionSeconds}"
    !state.cameraReady && state.phase != RoutinePhase.PAUSED -> "Keep the phone where you can comfortably see yourself"
    state.phase == RoutinePhase.HOLDING -> "Stay with it while the alarm fades"
    state.phase == RoutinePhase.PAUSED -> "Your time is saved"
    !state.framed -> state.framingHint
    else -> poseInstruction(state.pose)
}

private fun poseInstruction(pose: YogaPose): String = when (pose) {
        YogaPose.MOUNTAIN -> "Stand tall with your arms relaxed"
        YogaPose.WARRIOR_TWO -> "Reach wide and bend either front knee"
        YogaPose.TREE -> "Balance with one foot lifted"
        YogaPose.CHAIR -> "Face sideways, sit your hips back and reach both arms up"
        YogaPose.FORWARD_FOLD -> "Face sideways, sit with straight legs and reach forward"
        YogaPose.TRIANGLE -> "Reach one hand down and the other straight up"
        YogaPose.GODDESS -> "Bend both knees wide and lift your hands"
        YogaPose.WIDE_LEG_FOLD -> "Fold between wide, straight legs"
}

@Composable
private fun RoutinePoseGuide(pose: YogaPose, frame: PoseFrame?, alpha: Float) {
    val geometry = remember(pose) { guideGeometry(pose) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val projection = frame?.let { PreviewProjection(it, size.width, size.height) }
        fun mapped(index: Int): Offset? {
            if (projection == null) return null
            val point = frame?.landmarks?.get(index)?.takeIf { it.isDrawable() } ?: return null
            return Offset(projection.x(point.x), projection.y(point.y))
        }
        val sidePose = pose == YogaPose.CHAIR || pose == YogaPose.FORWARD_FOLD
        val side = if ((frame?.landmarks?.get(11)?.visibility ?: 0f) >= (frame?.landmarks?.get(12)?.visibility ?: 0f)) 0 else 1
        val leftShoulder = mapped(11)
        val rightShoulder = mapped(12)
        val leftHip = mapped(23)
        val rightHip = mapped(24)
        val shoulderMid = if (sidePose) mapped(11 + side) ?: Offset(size.width / 2f, size.height * 0.4f)
            else if (leftShoulder != null && rightShoulder != null) midpoint(leftShoulder, rightShoulder)
            else Offset(size.width / 2f, size.height * 0.4f)
        val hipMid = if (sidePose) mapped(23 + side) ?: Offset(shoulderMid.x, shoulderMid.y + size.height * 0.17f)
            else if (leftHip != null && rightHip != null) midpoint(leftHip, rightHip)
            else Offset(shoulderMid.x, shoulderMid.y + size.height * 0.17f)
        val shoulderWidth = if (leftShoulder != null && rightShoulder != null)
            distance(leftShoulder, rightShoulder).coerceAtLeast(size.width * 0.12f) else size.width * 0.2f
        val torsoHeight = distance(shoulderMid, hipMid).coerceAtLeast(shoulderWidth * 0.8f)
        var origin = shoulderMid
        var xScale = if (sidePose) torsoHeight else shoulderWidth
        var yScale = torsoHeight
        if (sidePose) {
            val forwardPoint = mapped(if (pose == YogaPose.CHAIR) 25 + side else 27 + side)
            if (forwardPoint != null && forwardPoint.x < hipMid.x) xScale = -xScale
        }
        if (pose == YogaPose.WIDE_LEG_FOLD) {
            origin = hipMid
            val leftFoot = mapped(27)
            val rightFoot = mapped(28)
            xScale = if (leftFoot != null && rightFoot != null) distance(leftFoot, rightFoot) / 2.5f else shoulderWidth
            yScale = if (leftFoot != null && rightFoot != null)
                ((midpoint(leftFoot, rightFoot).y - hipMid.y) / 1.8f).coerceAtLeast(shoulderWidth * 0.4f) else torsoHeight
        }
        fun guidePoint(point: Offset) = Offset(origin.x + point.x * xScale, origin.y + point.y * yScale)
        val bodyWidth = maxOf(26.dp.toPx(), shoulderWidth * 0.5f)
        val guideColor = Color(0xFFB7BDB8).copy(alpha = 0.32f * alpha)
        geometry.segments.forEach { (from, to) ->
            drawLine(guideColor, guidePoint(from), guidePoint(to), bodyWidth, StrokeCap.Round)
        }
        drawCircle(guideColor, bodyWidth * 0.52f, guidePoint(geometry.head))
    }
}

private data class GuideGeometry(
    val head: Offset,
    val segments: List<Pair<Offset, Offset>>,
)

private fun guideGeometry(pose: YogaPose): GuideGeometry = when (pose) {
    YogaPose.WARRIOR_TWO -> GuideGeometry(
        Offset(0f, -0.55f),
        listOf(
            Offset(0f, 0f) to Offset(0f, 1f),
            Offset(-0.5f, 0f) to Offset(-1.05f, 0f), Offset(-1.05f, 0f) to Offset(-1.65f, 0f),
            Offset(0.5f, 0f) to Offset(1.05f, 0f), Offset(1.05f, 0f) to Offset(1.65f, 0f),
            Offset(-0.24f, 1f) to Offset(-0.95f, 1.75f), Offset(-0.95f, 1.75f) to Offset(-1.45f, 2.55f),
            Offset(0.24f, 1f) to Offset(0.9f, 1.65f), Offset(0.9f, 1.65f) to Offset(1.45f, 2.45f),
        ),
    )
    YogaPose.TREE -> GuideGeometry(
        Offset(0f, -0.55f),
        listOf(
            Offset(0f, 0f) to Offset(0f, 1f),
            Offset(-0.5f, 0f) to Offset(-0.78f, -0.55f), Offset(-0.78f, -0.55f) to Offset(-0.18f, -1.02f),
            Offset(0.5f, 0f) to Offset(0.78f, -0.55f), Offset(0.78f, -0.55f) to Offset(0.18f, -1.02f),
            Offset(-0.24f, 1f) to Offset(-0.25f, 2.55f),
            Offset(0.24f, 1f) to Offset(0.9f, 1.62f), Offset(0.9f, 1.62f) to Offset(-0.12f, 1.95f),
        ),
    )
    YogaPose.CHAIR -> GuideGeometry(
        Offset(0.02f, -0.28f),
        listOf(
            Offset(0f, 0f) to Offset(-0.35f, 0.92f),
            Offset(0f, 0f) to Offset(0.38f, -0.48f), Offset(0.38f, -0.48f) to Offset(0.75f, -0.95f),
            Offset(-0.35f, 0.92f) to Offset(0.2f, 1.45f), Offset(0.2f, 1.45f) to Offset(-0.15f, 2.2f),
        ),
    )
    YogaPose.FORWARD_FOLD -> GuideGeometry(
        Offset(0.12f, -0.25f),
        listOf(
            Offset(0f, 0f) to Offset(-0.55f, 0.8f),
            Offset(0f, 0f) to Offset(0.65f, 0.15f), Offset(0.65f, 0.15f) to Offset(1.1f, 0.23f),
            Offset(-0.55f, 0.8f) to Offset(0.35f, 0.85f), Offset(0.35f, 0.85f) to Offset(1.25f, 0.9f),
        ),
    )
    YogaPose.TRIANGLE -> GuideGeometry(
        Offset(-0.42f, -0.34f),
        listOf(
            Offset(0f, 0f) to Offset(0.72f, 1f),
            Offset(-0.12f, 0f) to Offset(-0.15f, -0.75f), Offset(-0.15f, -0.75f) to Offset(-0.18f, -1.55f),
            Offset(0.12f, 0f) to Offset(0.16f, 0.8f), Offset(0.16f, 0.8f) to Offset(0.18f, 1.62f),
            Offset(0.52f, 1f) to Offset(-0.72f, 1.75f), Offset(-0.72f, 1.75f) to Offset(-1.45f, 2.45f),
            Offset(0.92f, 1f) to Offset(1.35f, 1.75f), Offset(1.35f, 1.75f) to Offset(1.8f, 2.42f),
        ),
    )
    YogaPose.GODDESS -> GuideGeometry(
        Offset(0f, -0.55f),
        listOf(
            Offset(0f, 0f) to Offset(0f, 1f),
            Offset(-0.5f, 0f) to Offset(-1.0f, 0.38f), Offset(-1.0f, 0.38f) to Offset(-0.98f, -0.45f),
            Offset(0.5f, 0f) to Offset(1.0f, 0.38f), Offset(1.0f, 0.38f) to Offset(0.98f, -0.45f),
            Offset(-0.24f, 1f) to Offset(-1.15f, 1.55f), Offset(-1.15f, 1.55f) to Offset(-1.52f, 2.35f),
            Offset(0.24f, 1f) to Offset(1.15f, 1.55f), Offset(1.15f, 1.55f) to Offset(1.52f, 2.35f),
        ),
    )
    YogaPose.WIDE_LEG_FOLD -> GuideGeometry(
        Offset(0f, 0.8f),
        listOf(
            Offset(0f, 0f) to Offset(0f, 0.5f),
            Offset(-0.5f, 0.5f) to Offset(-0.5f, 1.1f), Offset(-0.5f, 1.1f) to Offset(-0.5f, 1.7f),
            Offset(0.5f, 0.5f) to Offset(0.5f, 1.1f), Offset(0.5f, 1.1f) to Offset(0.5f, 1.7f),
            Offset(-0.2f, 0f) to Offset(-0.75f, 0.9f), Offset(-0.75f, 0.9f) to Offset(-1.25f, 1.8f),
            Offset(0.2f, 0f) to Offset(0.75f, 0.9f), Offset(0.75f, 0.9f) to Offset(1.25f, 1.8f),
        ),
    )
    YogaPose.MOUNTAIN -> GuideGeometry(
        Offset(0f, -0.55f),
        listOf(
            Offset(0f, 0f) to Offset(0f, 1f),
            Offset(-0.5f, 0f) to Offset(-0.62f, 0.7f), Offset(-0.62f, 0.7f) to Offset(-0.52f, 1.4f),
            Offset(0.5f, 0f) to Offset(0.62f, 0.7f), Offset(0.62f, 0.7f) to Offset(0.52f, 1.4f),
            Offset(-0.24f, 1f) to Offset(-0.3f, 2.55f), Offset(0.24f, 1f) to Offset(0.3f, 2.55f),
        ),
    )
}

private fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
private fun distance(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

@Composable
private fun RoutineSkeletonOverlay(frame: PoseFrame?, detected: Boolean) {
    val landmarks = frame?.landmarks ?: return
    if (frame.imageWidth == 0 || frame.imageHeight == 0) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val projection = PreviewProjection(frame, size.width, size.height)
        fun BodyPoint.toCanvasOffset() = Offset(projection.x(x), projection.y(y))
        val color = if (detected) Lime else Color.White.copy(alpha = 0.75f)
        routineSkeletonConnections.forEach { (startIndex, endIndex) ->
            val start = landmarks[startIndex]
            val end = landmarks[endIndex]
            if (start != null && end != null && start.isDrawable() && end.isDrawable()) {
                drawLine(color, start.toCanvasOffset(), end.toCanvasOffset(), 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

private fun BodyPoint.isDrawable() = visibility >= 0.35f && presence >= 0.35f

private val routineSkeletonConnections = listOf(
    11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27,
    27 to 29, 29 to 31, 24 to 26, 26 to 28, 28 to 30, 30 to 32,
)

private val Lime = Color(0xFFC9EE73)
