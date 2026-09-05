package com.lurremcfly.yogaalarm.model

data class PoseFrame(
    val landmarks: BodyLandmarks?,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val capturedAtMs: Long = 0L,
)
