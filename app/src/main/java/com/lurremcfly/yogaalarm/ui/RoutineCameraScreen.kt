package com.lurremcfly.yogaalarm.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.core.view.doOnLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBack: () -> Unit,
) {
    var cameraAttempt by remember { mutableStateOf(0) }
    var previewReady by remember { mutableStateOf(false) }
    val guidePose = if (uiState.phase == RoutinePhase.TRANSITION) uiState.nextPose ?: uiState.pose else uiState.pose

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07100A)),
    ) {
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text(
                        if (showBack) "‹ Alarm" else "Stop",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (uiState.phase != RoutinePhase.COMPLETE) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PoseGlyph(guidePose, Modifier.size(96.dp))
                        Text(guidePose.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(64.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState.phase) {
                RoutinePhase.HOLDING -> {
                    RoutineCountdown(progress, uiState.remainingSeconds)
                    Spacer(Modifier.height(48.dp))
                }
                RoutinePhase.PAUSED -> {
                    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f)) {
                        Text("↶ Return to pose", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${uiState.remainingSeconds} sec remaining", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    if (!uiState.framed || uiState.error != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(uiState.error ?: uiState.framingHint, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(36.dp))
                }
                else -> {
                    Text(statusTitle(uiState, previewReady), color = Color.White,
                        fontSize = if (uiState.phase == RoutinePhase.COMPLETE) 32.sp else 20.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(7.dp))
                    Text(statusDetail(uiState), color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }

            if (uiState.phase == RoutinePhase.COMPLETE) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color(0xFF173421)),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("Done", modifier = Modifier.padding(horizontal = 42.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                }
            } else if (uiState.error != null) {
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
        val observer = Observer<PreviewView.StreamState> { onPreviewReady(it == PreviewView.StreamState.STREAMING) }
        preview.previewStreamState.observe(lifecycleOwner, observer)
        preview.doOnLayout { controller.bind(lifecycleOwner, preview) }
        onDispose {
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
private fun RoutineCountdown(progress: StateFlow<RoutineProgress>, seconds: Int) {
    val value by progress.collectAsStateWithLifecycle()
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { value.hold },
            modifier = Modifier.size(144.dp),
            color = Lime.copy(alpha = 0.55f),
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeWidth = 2.dp,
            strokeCap = StrokeCap.Round,
        )
        Text(seconds.toString(), color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Medium)
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
        fun mapped(index: Int): Offset? = frame?.landmarks?.get(index)?.takeIf { it.isDrawable() }?.let {
            Offset(projection!!.x(it.x), projection.y(it.y))
        }
        val sidePose = pose == YogaPose.CHAIR || pose == YogaPose.FORWARD_FOLD
        val side = if ((frame?.landmarks?.get(11)?.visibility ?: 0f) >= (frame?.landmarks?.get(12)?.visibility ?: 0f)) 0 else 1
        val leftShoulder = mapped(11)
        val rightShoulder = mapped(12)
        val shoulderMid = if (sidePose) mapped(11 + side) ?: Offset(size.width / 2f, size.height * 0.4f)
            else if (leftShoulder != null && rightShoulder != null) midpoint(leftShoulder, rightShoulder)
            else Offset(size.width / 2f, size.height * 0.4f)
        val hipMid = if (sidePose) mapped(23 + side) ?: Offset(shoulderMid.x, shoulderMid.y + size.height * 0.17f)
            else if (mapped(23) != null && mapped(24) != null) midpoint(mapped(23)!!, mapped(24)!!)
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
        fun guidePoint(point: Pair<Float, Float>) = Offset(origin.x + point.first * xScale, origin.y + point.second * yScale)
        val bodyWidth = maxOf(26.dp.toPx(), shoulderWidth * 0.5f)
        val guideColor = Color(0xFFB7BDB8).copy(alpha = 0.32f * alpha)
        geometry.segments.forEach { (from, to) ->
            drawLine(guideColor, guidePoint(from), guidePoint(to), bodyWidth, StrokeCap.Round)
        }
        drawCircle(guideColor, bodyWidth * 0.52f, guidePoint(geometry.head))
    }
}

private data class GuideGeometry(
    val head: Pair<Float, Float>,
    val segments: List<Pair<Pair<Float, Float>, Pair<Float, Float>>>,
)

private fun guideGeometry(pose: YogaPose): GuideGeometry = when (pose) {
    YogaPose.WARRIOR_TWO -> GuideGeometry(
        0f to -0.55f,
        listOf(
            (0f to 0f) to (0f to 1f),
            (-0.5f to 0f) to (-1.05f to 0f), (-1.05f to 0f) to (-1.65f to 0f),
            (0.5f to 0f) to (1.05f to 0f), (1.05f to 0f) to (1.65f to 0f),
            (-0.24f to 1f) to (-0.95f to 1.75f), (-0.95f to 1.75f) to (-1.45f to 2.55f),
            (0.24f to 1f) to (0.9f to 1.65f), (0.9f to 1.65f) to (1.45f to 2.45f),
        ),
    )
    YogaPose.TREE -> GuideGeometry(
        0f to -0.55f,
        listOf(
            (0f to 0f) to (0f to 1f),
            (-0.5f to 0f) to (-0.78f to -0.55f), (-0.78f to -0.55f) to (-0.18f to -1.02f),
            (0.5f to 0f) to (0.78f to -0.55f), (0.78f to -0.55f) to (0.18f to -1.02f),
            (-0.24f to 1f) to (-0.25f to 2.55f),
            (0.24f to 1f) to (0.9f to 1.62f), (0.9f to 1.62f) to (-0.12f to 1.95f),
        ),
    )
    YogaPose.CHAIR -> GuideGeometry(
        0.02f to -0.28f,
        listOf(
            (0f to 0f) to (-0.35f to 0.92f),
            (0f to 0f) to (0.38f to -0.48f), (0.38f to -0.48f) to (0.75f to -0.95f),
            (-0.35f to 0.92f) to (0.2f to 1.45f), (0.2f to 1.45f) to (-0.15f to 2.2f),
        ),
    )
    YogaPose.FORWARD_FOLD -> GuideGeometry(
        0.12f to -0.25f,
        listOf(
            (0f to 0f) to (-0.55f to 0.8f),
            (0f to 0f) to (0.65f to 0.15f), (0.65f to 0.15f) to (1.1f to 0.23f),
            (-0.55f to 0.8f) to (0.35f to 0.85f), (0.35f to 0.85f) to (1.25f to 0.9f),
        ),
    )
    YogaPose.TRIANGLE -> GuideGeometry(
        -0.42f to -0.34f,
        listOf(
            (0f to 0f) to (0.72f to 1f),
            (-0.12f to 0f) to (-0.15f to -0.75f), (-0.15f to -0.75f) to (-0.18f to -1.55f),
            (0.12f to 0f) to (0.16f to 0.8f), (0.16f to 0.8f) to (0.18f to 1.62f),
            (0.52f to 1f) to (-0.72f to 1.75f), (-0.72f to 1.75f) to (-1.45f to 2.45f),
            (0.92f to 1f) to (1.35f to 1.75f), (1.35f to 1.75f) to (1.8f to 2.42f),
        ),
    )
    YogaPose.GODDESS -> GuideGeometry(
        0f to -0.55f,
        listOf(
            (0f to 0f) to (0f to 1f),
            (-0.5f to 0f) to (-1.0f to 0.38f), (-1.0f to 0.38f) to (-0.98f to -0.45f),
            (0.5f to 0f) to (1.0f to 0.38f), (1.0f to 0.38f) to (0.98f to -0.45f),
            (-0.24f to 1f) to (-1.15f to 1.55f), (-1.15f to 1.55f) to (-1.52f to 2.35f),
            (0.24f to 1f) to (1.15f to 1.55f), (1.15f to 1.55f) to (1.52f to 2.35f),
        ),
    )
    YogaPose.WIDE_LEG_FOLD -> GuideGeometry(
        0f to 0.8f,
        listOf(
            (0f to 0f) to (0f to 0.5f),
            (-0.5f to 0.5f) to (-0.5f to 1.1f), (-0.5f to 1.1f) to (-0.5f to 1.7f),
            (0.5f to 0.5f) to (0.5f to 1.1f), (0.5f to 1.1f) to (0.5f to 1.7f),
            (-0.2f to 0f) to (-0.75f to 0.9f), (-0.75f to 0.9f) to (-1.25f to 1.8f),
            (0.2f to 0f) to (0.75f to 0.9f), (0.75f to 0.9f) to (1.25f to 1.8f),
        ),
    )
    YogaPose.MOUNTAIN -> GuideGeometry(
        0f to -0.55f,
        listOf(
            (0f to 0f) to (0f to 1f),
            (-0.5f to 0f) to (-0.62f to 0.7f), (-0.62f to 0.7f) to (-0.52f to 1.4f),
            (0.5f to 0f) to (0.62f to 0.7f), (0.62f to 0.7f) to (0.52f to 1.4f),
            (-0.24f to 1f) to (-0.3f to 2.55f), (0.24f to 1f) to (0.3f to 2.55f),
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
