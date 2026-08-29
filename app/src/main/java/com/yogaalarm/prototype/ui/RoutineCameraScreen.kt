package com.yogaalarm.prototype.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.yogaalarm.prototype.audio.PrototypeAlarmAudio
import com.yogaalarm.prototype.camera.PoseCameraController
import com.yogaalarm.prototype.model.BodyLandmarks
import com.yogaalarm.prototype.model.BodyPoint
import com.yogaalarm.prototype.model.PoseFrame
import com.yogaalarm.prototype.model.YogaPose
import kotlin.math.min
import kotlin.math.hypot

@Composable
fun RoutineCameraScreen(
    uiState: RoutineUiState,
    onPoseFrame: (PoseFrame) -> Unit,
    onCameraError: (String) -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        PoseCameraController(context, onPoseFrame, onCameraError)
    }
    val audio = remember(uiState.alarmSound) { PrototypeAlarmAudio(context.applicationContext, uiState.alarmSound) }

    DisposableEffect(controller, uiState.soundEnabled) {
        if (uiState.soundEnabled) audio.start()
        onDispose {
            controller.close()
            audio.close()
        }
    }
    LaunchedEffect(uiState.alarmLevel) {
        audio.setLevel(uiState.alarmLevel)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07100A)),
    ) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller.bind(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.14f)),
        )

        RoutinePoseGuide(
            pose = uiState.pose,
            landmarks = uiState.landmarks,
            imageWidth = uiState.imageWidth,
            imageHeight = uiState.imageHeight,
            detected = uiState.detected,
        )

        RoutineSkeletonOverlay(
            landmarks = uiState.landmarks,
            imageWidth = uiState.imageWidth,
            imageHeight = uiState.imageHeight,
            detected = uiState.detected,
        )

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
                LinearProgressIndicator(
                    progress = { uiState.routineProgress },
                    modifier = Modifier.weight(1f),
                    color = Lime,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
                Spacer(Modifier.width(12.dp))
                Text(uiState.pose.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            if (uiState.phase == RoutinePhase.HOLDING) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { uiState.holdProgress },
                        modifier = Modifier.width(118.dp).height(118.dp),
                        color = Lime,
                        trackColor = Color.White.copy(alpha = 0.18f),
                        strokeWidth = 5.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = uiState.remainingSeconds.toString(),
                        color = Color.White,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(
                text = statusTitle(uiState),
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
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color(0xFF173421)),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        if (showBack) "Back to alarm" else "Stop alarm",
                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun statusTitle(state: RoutineUiState): String = when {
    state.error != null -> "Camera unavailable"
    state.phase == RoutinePhase.COMPLETE -> "Good morning ☀️"
    state.phase == RoutinePhase.TRANSITION -> "Nice"
    state.phase == RoutinePhase.HOLDING -> "Hold"
    state.phase == RoutinePhase.PAUSED -> "Return to the pose"
    !state.framed -> "Move into view"
    state.poseScore >= 0.55f -> "Almost there"
    else -> "Move into the pose"
}

private fun statusDetail(state: RoutineUiState): String = when {
    state.error != null -> state.error
    state.phase == RoutinePhase.COMPLETE -> "${state.poseCount} poses completed"
    state.phase == RoutinePhase.TRANSITION -> "Next pose in ${state.transitionSeconds}"
    state.phase == RoutinePhase.HOLDING -> "Stay with it while the alarm fades"
    state.phase == RoutinePhase.PAUSED -> "Your time is saved"
    !state.framed -> "Keep your head, hands, hips and knees visible"
    else -> when (state.pose) {
        YogaPose.MOUNTAIN -> "Stand tall with your arms relaxed"
        YogaPose.WARRIOR_TWO -> "Reach wide and bend either front knee"
        YogaPose.TREE -> "Balance with one foot lifted"
        else -> "Follow the pose guide"
    }
}

@Composable
private fun RoutinePoseGuide(
    pose: YogaPose,
    landmarks: BodyLandmarks?,
    imageWidth: Int,
    imageHeight: Int,
    detected: Boolean,
) {
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
        val geometry = guideGeometry(pose)
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
    else -> GuideGeometry(
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
