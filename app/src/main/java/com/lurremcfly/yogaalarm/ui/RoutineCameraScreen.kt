package com.lurremcfly.yogaalarm.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.lurremcfly.yogaalarm.model.BodyLandmarks
import com.lurremcfly.yogaalarm.model.BodyPoint
import com.lurremcfly.yogaalarm.model.PoseFrame
import com.lurremcfly.yogaalarm.model.YogaPose
import kotlin.math.min
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

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.14f)),
        )

        if (uiState.phase != RoutinePhase.COMPLETE) {
            RoutineOverlays(frames, guidePose, uiState.detected && uiState.phase != RoutinePhase.TRANSITION)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text(
                        if (showBack) "‹ Alarm" else "Stop",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${uiState.poseIndex + 1} / ${uiState.poseCount}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(12.dp))
                RoutineOverallProgress(progress, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(guidePose.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            if (uiState.phase == RoutinePhase.HOLDING || uiState.phase == RoutinePhase.PAUSED) {
                RoutineCountdown(progress, uiState.remainingSeconds, uiState.phase == RoutinePhase.PAUSED)
                Spacer(Modifier.height(14.dp))
            }

            Text(
                text = statusTitle(uiState, previewReady),
                color = Color.White,
                fontSize = if (uiState.phase == RoutinePhase.COMPLETE) 38.sp else 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = statusDetail(uiState),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

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
            scaleType = PreviewView.ScaleType.FIT_CENTER
            // Keep TextureView compatibility and the existing selfie overlay alignment.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { onPreviewReady(it == PreviewView.StreamState.STREAMING) }
        preview.previewStreamState.observe(lifecycleOwner, observer)
        controller.bind(lifecycleOwner, preview)
        onDispose {
            preview.previewStreamState.removeObserver(observer)
            controller.close()
        }
    }
    AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun RoutineOverlays(frames: StateFlow<PoseFrame?>, pose: YogaPose, detected: Boolean) {
    val frame by frames.collectAsStateWithLifecycle()
    RoutinePoseGuide(pose, frame?.landmarks, frame?.imageWidth ?: 0, frame?.imageHeight ?: 0, detected)
    RoutineSkeletonOverlay(frame?.landmarks, frame?.imageWidth ?: 0, frame?.imageHeight ?: 0, detected)
}

@Composable
private fun RoutineOverallProgress(progress: StateFlow<RoutineProgress>, modifier: Modifier) {
    val value by progress.collectAsStateWithLifecycle()
    LinearProgressIndicator(progress = { value.routine }, modifier = modifier, color = Lime, trackColor = Color.White.copy(alpha = 0.2f))
}

@Composable
private fun RoutineCountdown(progress: StateFlow<RoutineProgress>, seconds: Int, paused: Boolean) {
    val value by progress.collectAsStateWithLifecycle()
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { value.hold },
            modifier = Modifier.width(118.dp).height(118.dp),
            color = if (paused) Color.White.copy(alpha = 0.55f) else Lime,
            trackColor = Color.White.copy(alpha = 0.18f),
            strokeWidth = 5.dp,
            strokeCap = StrokeCap.Round,
        )
        Text(seconds.toString(), color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Medium)
    }
}

private fun statusTitle(state: RoutineUiState, previewReady: Boolean): String = when {
    state.error != null -> "Camera unavailable"
    state.phase == RoutinePhase.COMPLETE -> "Good morning ☀️"
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
        YogaPose.CHAIR -> "Sit your hips back and reach both arms up"
        YogaPose.FORWARD_FOLD -> "Sit, extend both legs and reach forward"
        YogaPose.TRIANGLE -> "Reach one hand down and the other straight up"
        YogaPose.GODDESS -> "Bend both knees wide and lift your hands"
        YogaPose.WIDE_LEG_FOLD -> "Fold between wide, straight legs"
}

@Composable
private fun RoutinePoseGuide(
    pose: YogaPose,
    landmarks: BodyLandmarks?,
    imageWidth: Int,
    imageHeight: Int,
    detected: Boolean,
) {
    val geometry = remember(pose) { guideGeometry(pose) }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val scale = if (imageWidth > 0 && imageHeight > 0) min(size.width / imageWidth, size.height / imageHeight) else 1f
        val offsetX = if (imageWidth > 0) (size.width - imageWidth * scale) / 2f else 0f
        val offsetY = if (imageHeight > 0) (size.height - imageHeight * scale) / 2f else 0f
        fun mapped(index: Int): Offset? = landmarks?.get(index)?.takeIf { it.isDrawable() }?.let {
            Offset(offsetX + it.x * imageWidth * scale, offsetY + it.y * imageHeight * scale)
        }

        val leftShoulder = mapped(11)
        val rightShoulder = mapped(12)
        val leftHip = mapped(23)
        val rightHip = mapped(24)
        val shoulderMid = if (leftShoulder != null && rightShoulder != null) midpoint(leftShoulder, rightShoulder) else Offset(size.width / 2f, size.height * 0.3f)
        val hipMid = if (leftHip != null && rightHip != null) midpoint(leftHip, rightHip) else Offset(shoulderMid.x, shoulderMid.y + size.height * 0.2f)
        val shoulderWidth = if (leftShoulder != null && rightShoulder != null) distance(leftShoulder, rightShoulder).coerceAtLeast(size.width * 0.12f) else min(size.width * 0.2f, size.height * 0.15f)
        val torsoHeight = distance(shoulderMid, hipMid).coerceAtLeast(shoulderWidth * 0.8f)
        fun guidePoint(point: Pair<Float, Float>) = Offset(shoulderMid.x + point.first * shoulderWidth, shoulderMid.y + point.second * torsoHeight)
        val bodyWidth = maxOf(42.dp.toPx(), shoulderWidth * 0.72f)

        drawRect(Color.Black.copy(alpha = 0.27f))
        geometry.segments.forEach { (from, to) ->
            drawLine(Color.Transparent, guidePoint(from), guidePoint(to), bodyWidth * 1.12f, StrokeCap.Round, blendMode = BlendMode.Clear)
        }
        val head = guidePoint(geometry.head)
        drawCircle(Color.Transparent, bodyWidth * 0.62f, head, blendMode = BlendMode.Clear)

        val guideColor = if (detected) Lime.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.5f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 10.dp.toPx()))
        geometry.segments.forEach { (from, to) ->
            drawLine(guideColor, guidePoint(from), guidePoint(to), 2.dp.toPx(), StrokeCap.Round, pathEffect = dash)
        }
        drawCircle(guideColor, bodyWidth * 0.38f, head, style = Stroke(2.dp.toPx(), pathEffect = dash))
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
        0f to -0.55f,
        listOf(
            (0f to 0f) to (0f to 1f),
            (-0.5f to 0f) to (-0.72f to -0.72f), (-0.72f to -0.72f) to (-0.28f to -1.5f),
            (0.5f to 0f) to (0.72f to -0.72f), (0.72f to -0.72f) to (0.28f to -1.5f),
            (-0.24f to 1f) to (-0.82f to 1.55f), (-0.82f to 1.55f) to (-0.62f to 2.45f),
            (0.24f to 1f) to (0.82f to 1.55f), (0.82f to 1.55f) to (0.62f to 2.45f),
        ),
    )
    YogaPose.FORWARD_FOLD -> GuideGeometry(
        0.2f to -0.35f,
        listOf(
            (0f to 0f) to (-1.05f to 0.55f),
            (-0.25f to 0f) to (0.7f to 0.18f), (0.7f to 0.18f) to (1.65f to 0.35f),
            (0.25f to 0f) to (0.78f to 0.35f), (0.78f to 0.35f) to (1.7f to 0.48f),
            (-1.05f to 0.55f) to (0.25f to 0.68f), (0.25f to 0.68f) to (1.68f to 0.72f),
            (-0.9f to 0.82f) to (0.3f to 0.92f), (0.3f to 0.92f) to (1.68f to 0.9f),
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
        0f to 0.58f,
        listOf(
            (0f to 0f) to (0f to -1f),
            (-0.5f to 0f) to (-0.68f to 0.7f), (-0.68f to 0.7f) to (-0.9f to 1.45f),
            (0.5f to 0f) to (0.68f to 0.7f), (0.68f to 0.7f) to (0.9f to 1.45f),
            (-0.24f to -1f) to (-1.05f to 0.1f), (-1.05f to 0.1f) to (-1.58f to 1.45f),
            (0.24f to -1f) to (1.05f to 0.1f), (1.05f to 0.1f) to (1.58f to 1.45f),
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
private fun RoutineSkeletonOverlay(
    landmarks: BodyLandmarks?,
    imageWidth: Int,
    imageHeight: Int,
    detected: Boolean,
) {
    if (landmarks == null || imageWidth == 0 || imageHeight == 0) return
    val color = if (detected) Lime else Color.White.copy(alpha = 0.82f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = min(size.width / imageWidth, size.height / imageHeight)
        val offsetX = (size.width - imageWidth * scale) / 2f
        val offsetY = (size.height - imageHeight * scale) / 2f
        fun BodyPoint.toCanvasOffset() = Offset(offsetX + x * imageWidth * scale, offsetY + y * imageHeight * scale)

        routineSkeletonConnections.forEach { (startIndex, endIndex) ->
            val start = landmarks[startIndex]
            val end = landmarks[endIndex]
            if (start != null && end != null && start.isDrawable() && end.isDrawable()) {
                drawLine(color.copy(alpha = 0.18f), start.toCanvasOffset(), end.toCanvasOffset(), 13.dp.toPx(), StrokeCap.Round)
                drawLine(color, start.toCanvasOffset(), end.toCanvasOffset(), 4.dp.toPx(), StrokeCap.Round)
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
