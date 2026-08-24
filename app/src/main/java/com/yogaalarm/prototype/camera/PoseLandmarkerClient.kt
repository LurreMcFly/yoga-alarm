package com.yogaalarm.prototype.camera

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
import com.yogaalarm.prototype.model.BodyLandmarks
import com.yogaalarm.prototype.model.BodyPoint
import com.yogaalarm.prototype.model.PoseFrame

class PoseLandmarkerClient(
    context: Context,
    private val onResult: (PoseFrame) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val landmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(MODEL_ASSET)
                    .build(),
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(MIN_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_CONFIDENCE)
            .setMinTrackingConfidence(MIN_CONFIDENCE)
            .setResultListener(::handleResult)
            .setErrorListener { error ->
                onError(error.message ?: "Pose detection failed")
            }
            .build(),
    )

    fun detect(imageProxy: ImageProxy) {
        val timestampMs = SystemClock.uptimeMillis()
        val width = imageProxy.width
        val height = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        imageProxy.use {
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
        }

        val transform = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(-1f, 1f, width / 2f, height / 2f)
        }
        val transformedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            transform,
            true,
        )
        val image = BitmapImageBuilder(transformedBitmap).build()
        landmarker.detectAsync(image, timestampMs)
    }

    override fun close() {
        landmarker.close()
    }

    private fun handleResult(result: PoseLandmarkerResult, inputImage: com.google.mediapipe.framework.image.MPImage) {
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
                inferenceTimeMs = SystemClock.uptimeMillis() - result.timestampMs(),
                imageWidth = inputImage.width,
                imageHeight = inputImage.height,
            ),
        )
    }

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
        const val MIN_CONFIDENCE = 0.5f
    }
}
