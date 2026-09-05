package com.lurremcfly.yogaalarm.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.lurremcfly.yogaalarm.model.BodyLandmarks
import com.lurremcfly.yogaalarm.model.BodyPoint
import com.lurremcfly.yogaalarm.model.PoseFrame
import java.nio.ByteBuffer

class PoseLandmarkerClient(
    context: Context,
    private val onResult: (PoseFrame) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private var bitmapBuffer: Bitmap? = null
    private var packedPixels: ByteBuffer? = null
    private var previousFrameAtMs = -MIN_FRAME_INTERVAL_MS
    private val landmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(MODEL_ASSET)
                    .build(),
            )
            // Sequential video inference retains tracking and lets us release each input
            // deterministically. This entire client runs on the camera analysis worker.
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(MIN_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_CONFIDENCE)
            .setMinTrackingConfidence(MIN_CONFIDENCE)
            .build(),
    )

    fun detect(imageProxy: ImageProxy) {
        val timestampMs = SystemClock.elapsedRealtime()
        if (timestampMs - previousFrameAtMs < MIN_FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        previousFrameAtMs = timestampMs
        val width = imageProxy.width
        val height = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        try {
            val bitmap = imageProxy.use {
                val target = bitmapBuffer?.takeIf { it.width == width && it.height == height }
                    ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        bitmapBuffer?.recycle()
                        bitmapBuffer = it
                    }
                val plane = imageProxy.planes[0]
                val buffer = plane.buffer
                buffer.rewind()
                if (plane.rowStride == width * 4) {
                    target.copyPixelsFromBuffer(buffer)
                } else {
                    // Some front cameras pad RGBA rows; omit padding without allocating per row.
                    val packed = packedPixels?.takeIf { it.capacity() == width * height * 4 }
                        ?: ByteBuffer.allocateDirect(width * height * 4).also { packedPixels = it }
                    packed.clear()
                    for (row in 0 until height) {
                        buffer.limit(buffer.capacity())
                        buffer.position(row * plane.rowStride)
                        buffer.limit(row * plane.rowStride + width * 4)
                        packed.put(buffer)
                    }
                    packed.flip()
                    target.copyPixelsFromBuffer(packed)
                }
                target
            }
            // Preserve the existing selfie mirroring, rotation, and close-range coordinates.
            val transform = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                postScale(-1f, 1f, width / 2f, height / 2f)
            }
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, width, height, transform, true)
            val image = BitmapImageBuilder(transformed).build()
            try {
                handleResult(landmarker.detectForVideo(image, timestampMs), image.width, image.height, timestampMs)
            } finally {
                image.close() // Releases the transformed bitmap after synchronous inference.
            }
        } catch (error: Exception) {
            onError(error.message ?: "Pose detection failed")
        }
    }

    override fun close() {
        landmarker.close()
        bitmapBuffer?.recycle()
        bitmapBuffer = null
        packedPixels = null
    }

    private fun handleResult(result: PoseLandmarkerResult, width: Int, height: Int, timestampMs: Long) {
        val landmarks = result.landmarks().firstOrNull()?.map { landmark ->
            BodyPoint(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0f),
                presence = landmark.presence().orElse(0f),
            )
        }?.let(::BodyLandmarks)

        onResult(
            PoseFrame(
                landmarks = landmarks,
                inferenceTimeMs = SystemClock.elapsedRealtime() - timestampMs,
                imageWidth = width,
                imageHeight = height,
                capturedAtMs = timestampMs,
            ),
        )
    }

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
        const val MIN_CONFIDENCE = 0.5f
        const val MIN_FRAME_INTERVAL_MS = 67L
    }
}
