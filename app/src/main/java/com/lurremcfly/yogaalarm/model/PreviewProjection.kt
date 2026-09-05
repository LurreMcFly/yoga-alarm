package com.lurremcfly.yogaalarm.model

import kotlin.math.max

// Landmarks refer to the full, rotated and mirrored analysis image. CameraX's shared
// viewport identifies which part of that image is visible in the full-screen preview.
class PreviewProjection(frame: PoseFrame, viewWidth: Float, viewHeight: Float) {
    private val imageWidth = frame.imageWidth.toFloat()
    private val imageHeight = frame.imageHeight.toFloat()
    private val scale = max(
        viewWidth / (imageWidth * (frame.cropRight - frame.cropLeft)).coerceAtLeast(1f),
        viewHeight / (imageHeight * (frame.cropBottom - frame.cropTop)).coerceAtLeast(1f),
    )
    private val offsetX = viewWidth / 2f - imageWidth * (frame.cropLeft + frame.cropRight) / 2f * scale
    private val offsetY = viewHeight / 2f - imageHeight * (frame.cropTop + frame.cropBottom) / 2f * scale

    fun x(normalizedX: Float): Float = offsetX + normalizedX * imageWidth * scale
    fun y(normalizedY: Float): Float = offsetY + normalizedY * imageHeight * scale
}
