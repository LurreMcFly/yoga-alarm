package com.yogaalarm.prototype.model

data class PoseFrame(
    val landmarks: BodyLandmarks?,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
)
