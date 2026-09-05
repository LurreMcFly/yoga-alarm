package com.lurremcfly.yogaalarm.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class PoseEvaluation(
    val score: Float,
    val framed: Boolean,
)

object PoseScoring {
    // Keep framing guidance aligned with the landmarks used by each scorer.
    fun framingHint(pose: YogaPose, body: BodyLandmarks?): String {
        val points = body?.points ?: return "Position yourself in the camera view"
        if (pose == YogaPose.CHAIR || pose == YogaPose.FORWARD_FOLD) {
            return if (visibleSide(points) != null) "Keep your side facing the camera"
            else "Turn sideways and include one full arm and leg, including your foot"
        }
        if (!framed(points, listOf(11, 12))) return "Bring your shoulders into view"
        if (pose != YogaPose.WIDE_LEG_FOLD && !framed(points, listOf(0))) return "Bring your head into view"
        if (pose != YogaPose.TREE && !framed(points, listOf(15, 16))) return "Bring your hands into view"
        if (!framed(points, listOf(23, 24))) return "Tilt the phone to include your hips"
        if (!framed(points, listOf(25, 26))) return "Tilt the phone to include your knees"
        if (pose == YogaPose.WIDE_LEG_FOLD && !framed(points, listOf(27, 28))) return "Tilt the phone to include your feet"
        if (pose !in listOf(YogaPose.TREE, YogaPose.FORWARD_FOLD, YogaPose.WIDE_LEG_FOLD) &&
            !framed(points, listOf(13, 14))) return "Bring your elbows into view"
        return "Stay where you can comfortably see the screen"
    }

    fun evaluate(pose: YogaPose, landmarks: BodyLandmarks?): PoseEvaluation {
        val evaluation = when (pose) {
        YogaPose.MOUNTAIN -> mountain(landmarks)
        YogaPose.WARRIOR_TWO -> warriorTwo(landmarks)
        YogaPose.TREE -> tree(landmarks)
        YogaPose.CHAIR -> chair(landmarks)
        YogaPose.FORWARD_FOLD -> forwardFold(landmarks)
        YogaPose.TRIANGLE -> triangle(landmarks)
        YogaPose.GODDESS -> goddess(landmarks)
        YogaPose.WIDE_LEG_FOLD -> wideLegFold(landmarks)
        }
        return evaluation
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
            framed)
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
            framed)
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

    // One complete visible side is enough; the far arm/leg may be occluded in profile.
    private fun visibleSide(points: List<BodyPoint>): Int? = (0..1)
        .filter { side -> framed(points, listOf(11, 13, 15, 23, 25, 27).map { it + side }) }
        .maxByOrNull { side ->
            listOf(11, 13, 15, 23, 25, 27).minOf { minOf(points[it + side].visibility, points[it + side].presence) }
        }

    private fun chair(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        val side = visibleSide(points) ?: return PoseEvaluation(0f, false)
        val shoulder = points[11 + side]
        val elbow = points[13 + side]
        val wrist = points[15 + side]
        val hip = points[23 + side]
        val knee = points[25 + side]
        val ankle = points[27 + side]
        val torso = distance(shoulder, hip).coerceAtLeast(0.08f)
        val bentKnee = rangeScore(angle(hip, knee, ankle), 55f, 75f, 140f, 165f)
        val raisedArm = minOf(
            greaterScore((shoulder.y - wrist.y) / torso, 0.05f, 0.5f),
            greaterScore(angle(shoulder, elbow, wrist), 115f, 160f),
        )
        val hipsBack = greaterScore(abs(knee.x - hip.x) / torso, 0.1f, 0.45f)
        val torsoUp = greaterScore((hip.y - shoulder.y) / torso, 0.35f, 0.8f)
        return PoseEvaluation(minOf(bentKnee, raisedArm, average(hipsBack, torsoUp)), true)
    }

    private fun forwardFold(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        val side = visibleSide(points) ?: return PoseEvaluation(0f, false)
        val shoulder = points[11 + side]
        val wrist = points[15 + side]
        val hip = points[23 + side]
        val knee = points[25 + side]
        val ankle = points[27 + side]
        val torso = distance(shoulder, hip).coerceAtLeast(0.08f)
        val leg = distance(hip, ankle).coerceAtLeast(0.08f)
        val direction = if (ankle.x >= hip.x) 1f else -1f
        val folded = greaterScore((shoulder.x - hip.x) * direction / torso, 0.08f, 0.45f)
        val seatedLeg = lessScore(abs(ankle.y - hip.y) / leg, 0.2f, 0.65f)
        val straightLeg = greaterScore(angle(hip, knee, ankle), 125f, 165f)
        val reaching = greaterScore((wrist.x - shoulder.x) * direction / torso, 0.15f, 0.75f)
        return PoseEvaluation(minOf(folded, seatedLeg, straightLeg, reaching), true)
    }

    private fun triangle(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        val framed = framed(points, listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26))
        val shoulderWidth = distance(points[11], points[12]).coerceAtLeast(0.05f)
        val shoulderMid = midpoint(points[11], points[12])
        val hipMid = midpoint(points[23], points[24])
        val torsoHeight = distance(shoulderMid, hipMid).coerceAtLeast(0.08f)
        val sideBend = greaterScore(abs(shoulderMid.x - hipMid.x) / shoulderWidth, 0.22f, 0.85f)
        val verticalArms = average(
            greaterScore(abs(points[15].y - points[16].y) / torsoHeight, 0.95f, 2.1f),
            lessScore(abs(points[15].x - points[16].x) / shoulderWidth, 0.85f, 1.85f),
            greaterScore(angle(points[11], points[13], points[15]), 125f, 165f),
            greaterScore(angle(points[12], points[14], points[16]), 125f, 165f),
        )
        val wideStance = greaterScore(distance(points[25], points[26]) / shoulderWidth, 0.8f, 1.65f)
        return PoseEvaluation(
            if (framed) weighted(listOf(sideBend, verticalArms, wideStance, straightLegScore(points, 0.72f)), listOf(0.29f, 0.32f, 0.24f, 0.15f)) else 0f,
            framed)
    }

    private fun goddess(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        val framed = framed(points, listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26))
        val shoulderWidth = distance(points[11], points[12]).coerceAtLeast(0.05f)
        val shoulderMid = midpoint(points[11], points[12])
        val hipMid = midpoint(points[23], points[24])
        val torsoHeight = distance(shoulderMid, hipMid).coerceAtLeast(0.08f)
        val cactusArms = average(
            greaterScore(distance(points[13], points[14]) / shoulderWidth, 1f, 1.7f),
            greaterScore((points[13].y - points[15].y) / torsoHeight, 0.06f, 0.45f),
            greaterScore((points[14].y - points[16].y) / torsoHeight, 0.06f, 0.45f),
            rangeScore(angle(points[11], points[13], points[15]), 45f, 65f, 120f, 145f),
            rangeScore(angle(points[12], points[14], points[16]), 45f, 65f, 120f, 145f),
        )
        val wideKnees = greaterScore(distance(points[25], points[26]) / shoulderWidth, 0.85f, 1.75f)
        val bentKnees = if (framed(points, listOf(27, 28), 0.28f)) {
            average(
                rangeScore(angle(points[23], points[25], points[27]), 55f, 70f, 135f, 155f),
                rangeScore(angle(points[24], points[26], points[28]), 55f, 70f, 135f, 155f),
            )
        } else {
            greaterScore(distance(points[25], points[26]) / shoulderWidth, 0.9f, 1.8f)
        }
        val upright = clamp(1f - abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.9f))
        return PoseEvaluation(
            if (framed) weighted(listOf(cactusArms, wideKnees, bentKnees, upright), listOf(0.3f, 0.25f, 0.27f, 0.18f)) else 0f,
            framed)
    }

    private fun wideLegFold(body: BodyLandmarks?): PoseEvaluation {
        val points = body?.points ?: return PoseEvaluation(0f, false)
        if (!framed(points, listOf(11, 12, 15, 16, 23, 24, 25, 26, 27, 28))) return PoseEvaluation(0f, false)
        val shoulderMid = midpoint(points[11], points[12])
        val hipMid = midpoint(points[23], points[24])
        val wristMid = midpoint(points[15], points[16])
        val leg = average(distance(points[23], points[27]), distance(points[24], points[28])).coerceAtLeast(0.08f)
        // A torso folded toward the camera is foreshortened; its head need not project below the hips.
        val folded = maxOf(
            lessScore(distance(shoulderMid, hipMid) / leg, 0.4f, 0.8f),
            greaterScore((shoulderMid.y - hipMid.y) / leg, 0f, 0.3f),
        )
        val handsDown = greaterScore((wristMid.y - hipMid.y) / leg, 0.2f, 0.65f)
        val wideStance = greaterScore(distance(points[27], points[28]) / leg, 0.65f, 1.35f)
        val straightLegs = straightLegScore(points, 0f)
        return PoseEvaluation(minOf(folded, wideStance, straightLegs, handsDown), true)
    }

    private fun warriorLegShape(points: List<BodyPoint>): Float {
        val leftKnee = angle(points[23], points[25], points[27])
        val rightKnee = angle(points[24], points[26], points[28])
        return maxOf(
            average(rangeScore(leftKnee, 65f, 85f, 145f, 160f), greaterScore(rightKnee, 135f, 165f)),
            average(rangeScore(rightKnee, 65f, 85f, 145f, 160f), greaterScore(leftKnee, 135f, 165f)),
        )
    }

    private fun straightLegScore(points: List<BodyPoint>, fallback: Float): Float =
        if (framed(points, listOf(27, 28), 0.28f)) {
            average(
                greaterScore(angle(points[23], points[25], points[27]), 125f, 165f),
                greaterScore(angle(points[24], points[26], points[28]), 125f, 165f),
            )
        } else {
            fallback
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
    private fun lessScore(value: Float, goodAtOrBelow: Float, zeroAtOrAbove: Float) =
        clamp((zeroAtOrAbove - value) / (zeroAtOrAbove - goodAtOrBelow))
    private fun rangeScore(value: Float, outerLow: Float, innerLow: Float, innerHigh: Float, outerHigh: Float) = when {
        value < outerLow || value > outerHigh -> 0f
        value < innerLow -> (value - outerLow) / (innerLow - outerLow)
        value <= innerHigh -> 1f
        else -> (outerHigh - value) / (outerHigh - innerHigh)
    }
    private fun weighted(values: List<Float>, weights: List<Float>) = values.zip(weights).sumOf { (value, weight) -> (value * weight).toDouble() }.toFloat()
}
