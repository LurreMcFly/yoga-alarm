package com.yogaalarm.prototype.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yogaalarm.prototype.camera.PoseCameraController
import com.yogaalarm.prototype.model.BodyLandmarks
import com.yogaalarm.prototype.model.BodyPoint
import com.yogaalarm.prototype.model.PoseFrame
import java.util.Locale
import kotlin.math.max

@Composable
fun CameraSpikeScreen(
    uiState: PoseDebugUiState,
    onPoseFrame: (PoseFrame) -> Unit,
    onCameraError: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        PoseCameraController(
            context = context,
            onResult = onPoseFrame,
            onError = onCameraError,
        )
    }

    DisposableEffect(controller) {
        onDispose(controller::close)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller.bind(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        SkeletonOverlay(
            landmarks = uiState.landmarks,
            imageWidth = uiState.imageWidth,
            imageHeight = uiState.imageHeight,
            fullBodyVisible = uiState.isFullBodyVisible,
        )

        StatusCard(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 52.dp),
        )

        DebugCard(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
        )

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 38.dp),
        ) {
            Text("‹ Back", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusCard(uiState: PoseDebugUiState, modifier: Modifier = Modifier) {
    val statusColor: Color
    val title: String
    val guidance: String
    when {
        uiState.error != null -> {
            statusColor = Color(0xFFFF8A80)
            title = "Camera unavailable"
            guidance = uiState.error
        }
        uiState.landmarks == null -> {
            statusColor = Color(0xFFFFD180)
            title = "Finding you…"
            guidance = "Stand where your full body is visible"
        }
        uiState.isFullBodyVisible -> {
            statusColor = Color(0xFFC7F36B)
            title = "Full body detected ✓"
            guidance = "You’re in the right spot"
        }
        else -> {
            statusColor = Color(0xFFFFD180)
            title = "Adjust your position"
            guidance = "Missing: ${uiState.missingLandmarks.take(3).joinToString()}"
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xD91A211C), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = statusColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = guidance,
            color = Color.White.copy(alpha = 0.76f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DebugCard(uiState: PoseDebugUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xD91A211C), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "POSE LAB",
                color = Color(0xFFC7F36B),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "MediaPipe Full · CPU",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric(
                label = "Result rate",
                value = String.format(Locale.US, "%.1f FPS", uiState.resultFps),
            )
            Metric(
                label = "Latency",
                value = uiState.inferenceTimeMs?.let { "$it ms" } ?: "—",
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SkeletonOverlay(
    landmarks: BodyLandmarks?,
    imageWidth: Int,
    imageHeight: Int,
    fullBodyVisible: Boolean,
) {
    if (landmarks == null || imageWidth == 0 || imageHeight == 0) return

    val color = if (fullBodyVisible) Color(0xFFC7F36B) else Color(0xFFFFD180)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = max(size.width / imageWidth, size.height / imageHeight)
        val offsetX = (size.width - imageWidth * scale) / 2f
        val offsetY = (size.height - imageHeight * scale) / 2f

        fun BodyPoint.toCanvasOffset() = Offset(
            x = offsetX + (x * imageWidth * scale),
            y = offsetY + (y * imageHeight * scale),
        )

        skeletonConnections.forEach { (startIndex, endIndex) ->
            val start = landmarks[startIndex]
            val end = landmarks[endIndex]
            if (start != null && end != null && start.isDrawable() && end.isDrawable()) {
                drawLine(
                    color = color.copy(alpha = 0.85f),
                    start = start.toCanvasOffset(),
                    end = end.toCanvasOffset(),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        landmarks.points.forEach { point ->
            if (point.isDrawable()) {
                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
                    center = point.toCanvasOffset(),
                )
            }
        }
    }
}

private fun BodyPoint.isDrawable(): Boolean =
    visibility >= 0.35f && presence >= 0.35f

private val skeletonConnections = listOf(
    11 to 12,
    11 to 13,
    13 to 15,
    12 to 14,
    14 to 16,
    11 to 23,
    12 to 24,
    23 to 24,
    23 to 25,
    25 to 27,
    27 to 29,
    29 to 31,
    24 to 26,
    26 to 28,
    28 to 30,
    30 to 32,
)
