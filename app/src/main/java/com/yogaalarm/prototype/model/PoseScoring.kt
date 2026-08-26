package com.yogaalarm.prototype.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class PoseEvaluation(
    val score: Float,
    val framed: Boolean,
)

object PoseScoring {
    fun evaluate(pose: YogaPose, landmarks: BodyLandmarks?): PoseEvaluation = when (pose) {
        YogaPose.MOUNTAIN -> mountain(landmarks)
        YogaPose.WARRIOR_TWO -> warriorTwo(landmarks)
        YogaPose.TREE -> tree(landmarks)
        else -> PoseEvaluation(0f, false)
    }

    private fun mountain(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        val framed = framed(points, listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26))
        val shoulderWidth = distance(points[11], points[12]).coerceAtLeast(0.05f)
        val shoulderMid = midpoint(points[11], points[12])
        val hipMid = midpoint(points[23], points[24])
        val upright = clamp(1f - abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.65f))
        val armsDown = average(
            greaterScore(points[15].y - points[11].y, 0.12f, 0.35f),
            greaterScore(points[16].y - points[12].y, 0.12f, 0.35f),
        )
        val straightArms = average(
            greaterScore(angle(points[11], points[13], points[15]), 130f, 165f),
            greaterScore(angle(points[12], points[14], points[16]), 130f, 165f),
        )
        val levelKnees = clamp(1f - abs(points[25].y - points[26].y) / 0.12f)
        val compactStance = rangeScore(distance(points[25], points[26]) / shoulderWidth, 0.2f, 0.42f, 1.25f, 1.65f)
        return PoseEvaluation(
            if (framed) weighted(listOf(upright, armsDown, straightArms, levelKnees, compactStance), listOf(0.25f, 0.27f, 0.16f, 0.14f, 0.18f)) else 0f,
            framed,
        )
    }

    private fun warriorTwo(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        val framed = framed(points, listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26))
        val shoulderWidth = distance(points[11], points[12]).coerceAtLeast(0.05f)
        val shoulderMid = midpoint(points[11], points[12])
        val hipMid = midpoint(points[23], points[24])
        val horizontalArms = average(
            clamp(1f - abs(points[15].y - points[11].y) / (shoulderWidth * 0.9f)),
            clamp(1f - abs(points[16].y - points[12].y) / (shoulderWidth * 0.9f)),
        )
        val straightArms = average(
            greaterScore(angle(points[11], points[13], points[15]), 140f, 170f),
            greaterScore(angle(points[12], points[14], points[16]), 140f, 170f),
        )
        val wideKnees = greaterScore(distance(points[25], points[26]) / shoulderWidth, 0.8f, 1.55f)
        val anklesVisible = framed(points, listOf(27, 28), 0.28f)
        val legShape = if (anklesVisible) warriorLegShape(points) else 0.72f
        val upright = clamp(1f - abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.85f))
        return PoseEvaluation(
            if (framed) weighted(listOf(horizontalArms, straightArms, wideKnees, legShape, upright), listOf(0.29f, 0.2f, 0.24f, 0.12f, 0.15f)) else 0f,
            framed,
        )
    }

    private fun tree(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        val framed = framed(points, listOf(0, 11, 12, 23, 24, 25, 26))
        val hipWidth = distance(points[23], points[24]).coerceAtLeast(0.04f)
        val shoulderWidth = distance(points[11], points[12]).coerceAtLeast(0.05f)
        val shoulderMid = midpoint(points[11], points[12])
        val hipMid = midpoint(points[23], points[24])
        val torsoHeight = distance(shoulderMid, hipMid).coerceAtLeast(0.08f)
        val upright = clamp(1f - abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.7f))
        val leftRaised = average(
            greaterScore((points[26].y - points[25].y) / torsoHeight, 0.08f, 0.5f),
            greaterScore(abs(points[25].x - points[23].x) / hipWidth, 0.4f, 1.25f),
            clamp(1f - abs(points[26].x - points[24].x) / (hipWidth * 0.9f)),
        )
        val rightRaised = average(
            greaterScore((points[25].y - points[26].y) / torsoHeight, 0.08f, 0.5f),
            greaterScore(abs(points[26].x - points[24].x) / hipWidth, 0.4f, 1.25f),
            clamp(1f - abs(points[25].x - points[23].x) / (hipWidth * 0.9f)),
        )
        return PoseEvaluation(if (framed) weighted(listOf(maxOf(leftRaised, rightRaised), upright), listOf(0.78f, 0.22f)) else 0f, framed)
    }

    private fun warriorLegShape(points: List<BodyPoint>): Float {
        val leftKnee = angle(points[23], points[25], points[27])
        val rightKnee = angle(points[24], points[26], points[28])
        return maxOf(
            average(rangeScore(leftKnee, 65f, 85f, 145f, 160f), greaterScore(rightKnee, 135f, 165f)),
            average(rangeScore(rightKnee, 65f, 85f, 145f, 160f), greaterScore(leftKnee, 135f, 165f)),
        )
    }

    private fun framed(points: List<BodyPoint>, indexes: List<Int>, minimumVisibility: Float = 0.38f): Boolean =
        indexes.all { index ->
            points.getOrNull(index)?.let { point ->
                point.visibility >= minimumVisibility && point.presence >= minimumVisibility && point.x in -0.08f..1.08f && point.y in -0.08f..1.08f
            } == true
        }

    private fun midpoint(a: BodyPoint, b: BodyPoint) = BodyPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f, 0f, 1f, 1f)
    private fun distance(a: BodyPoint, b: BodyPoint) = hypot(a.x - b.x, a.y - b.y)
    private fun angle(a: BodyPoint, b: BodyPoint, c: BodyPoint): Float {
        var degrees = abs(Math.toDegrees((atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)).toDouble())).toFloat()
        if (degrees > 180f) degrees = 360f - degrees
        return degrees
    }
    private fun clamp(value: Float) = value.coerceIn(0f, 1f)
    private fun average(vararg values: Float) = values.average().toFloat()
    private fun greaterScore(value: Float, low: Float, high: Float) = clamp((value - low) / (high - low))
    private fun rangeScore(value: Float, outerLow: Float, innerLow: Float, innerHigh: Float, outerHigh: Float) = when {
        value < outerLow || value > outerHigh -> 0f
        value < innerLow -> (value - outerLow) / (innerLow - outerLow)
        value <= innerHigh -> 1f
        else -> (outerHigh - value) / (outerHigh - innerHigh)
    }
    private fun weighted(values: List<Float>, weights: List<Float>) = values.zip(weights).sumOf { (value, weight) -> (value * weight).toDouble() }.toFloat()
}
