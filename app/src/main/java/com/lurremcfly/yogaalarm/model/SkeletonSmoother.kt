package com.lurremcfly.yogaalarm.model

import kotlin.math.exp
import kotlin.math.hypot

/** Display-only filtering. Pose acceptance must continue to use the original landmarks. */
class SkeletonSmoother {
    private var previousFrame: PoseFrame? = null
    private var accepted = mutableListOf<BodyPoint?>()
    private var candidates = mutableListOf<BodyPoint?>()
    private var acceptedAt = mutableListOf<Long>()

    fun smooth(frame: PoseFrame): PoseFrame {
        val body = frame.landmarks
        val previous = previousFrame
        val elapsed = frame.capturedAtMs - (previous?.capturedAtMs ?: frame.capturedAtMs)
        if (body == null) {
            reset()
            return frame
        }
        if (previous == null || elapsed !in 1..250 || accepted.size != body.points.size ||
            frame.imageWidth != previous.imageWidth || frame.imageHeight != previous.imageHeight ||
            frame.cropLeft != previous.cropLeft || frame.cropTop != previous.cropTop ||
            frame.cropRight != previous.cropRight || frame.cropBottom != previous.cropBottom) {
            accepted = body.points.map { it.takeIf { point -> point.isReliable() } }.toMutableList()
            candidates = MutableList(body.points.size) { null }
            acceptedAt = MutableList(body.points.size) { frame.capturedAtMs }
            previousFrame = frame
            return frame.copy(landmarks = BodyLandmarks(body.points.map {
                if (it.isReliable()) it else it.copy(visibility = 0f, presence = 0f)
            }))
        }
        val blend = (1.0 - exp(-elapsed / 80.0)).toFloat()
        val torso = listOf(11 to 23, 12 to 24).mapNotNull { (a, b) ->
            val shoulder = body[a]?.takeIf { it.isReliable() }
            val hip = body[b]?.takeIf { it.isReliable() }
            if (shoulder != null && hip != null) distance(shoulder, hip) else null
        }.average().toFloat().takeIf { it.isFinite() } ?: 0.25f
        val jumpLimit = (torso * 0.55f).coerceIn(0.08f, 0.2f)
        val filtered = body.points.mapIndexed { index, point ->
            val prior = accepted[index]
            if (!point.isReliable()) {
                accepted[index] = null
                candidates[index] = null
                point.copy(visibility = 0f, presence = 0f)
            } else if (prior != null && distance(prior, point) > jumpLimit) {
                val candidate = candidates[index]
                candidates[index] = point
                if (candidate != null && distance(candidate, point) < jumpLimit * 0.5f) {
                    accepted[index] = point
                    acceptedAt[index] = frame.capturedAtMs
                    candidates[index] = null
                    point
                } else if (frame.capturedAtMs - acceptedAt[index] <= 100L) {
                    prior
                } else {
                    point.copy(visibility = 0f, presence = 0f)
                }
            } else {
                val next = if (prior == null) point else point.copy(
                    x = prior.x + (point.x - prior.x) * blend,
                    y = prior.y + (point.y - prior.y) * blend,
                    z = prior.z + (point.z - prior.z) * blend,
                )
                accepted[index] = next
                acceptedAt[index] = frame.capturedAtMs
                candidates[index] = null
                next
            }
        }
        previousFrame = frame
        return frame.copy(landmarks = BodyLandmarks(filtered))
    }

    fun reset() {
        previousFrame = null
        accepted.clear()
        candidates.clear()
        acceptedAt.clear()
    }

    private fun BodyPoint.isReliable() = visibility >= 0.35f && presence >= 0.35f && x.isFinite() && y.isFinite()
    private fun distance(a: BodyPoint, b: BodyPoint) = hypot(a.x - b.x, a.y - b.y)
}
