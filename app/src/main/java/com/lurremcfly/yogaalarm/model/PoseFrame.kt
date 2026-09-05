package com.lurremcfly.yogaalarm.model

data class PoseFrame(
    val landmarks: BodyLandmarks?,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val capturedAtMs: Long = 0L,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
)
