package com.lurremcfly.yogaalarm.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SquatScoringTest {
    private fun squat(): BodyLandmarks {
        val points = MutableList(33) { BodyPoint(0f, 0f, 0f, 0f, 0f) }
        listOf(
            11 to (0.36f to 0.30f), 12 to (0.64f to 0.30f),
            15 to (0.49f to 0.40f), 16 to (0.51f to 0.40f),
            23 to (0.43f to 0.62f), 24 to (0.57f to 0.62f),
            25 to (0.25f to 0.62f), 26 to (0.75f to 0.62f),
            27 to (0.30f to 0.86f), 28 to (0.70f to 0.86f),
        ).forEach { (index, xy) -> points[index] = BodyPoint(xy.first, xy.second, 0f, 1f, 1f) }
        return BodyLandmarks(points)
    }

    @Test
    fun frontalSquatWorksWithoutHeadOrElbowsAndWithEitherWristOccluded() {
        for (missing in listOf(-1, 15, 16)) {
            val body = BodyLandmarks(squat().points.mapIndexed { index, point ->
                if (index == missing) point.copy(visibility = 0f, presence = 0f) else point
            })
            val evaluation = PoseScoring.evaluate(YogaPose.SQUAT, body)
            assertTrue(evaluation.framed)
            assertTrue("Missing wrist=$missing score=${evaluation.score}", evaluation.score >= 0.68f)
        }
    }

    @Test
    fun lowCameraProjectionAcceptsKneesAboveHipsAndHandsNearShoulderHeight() {
        val points = squat().points.mapIndexed { index, point ->
            when (index) {
                15, 16 -> point.copy(y = 0.28f)
                23, 24 -> point.copy(y = 0.70f)
                25, 26 -> point.copy(y = 0.56f)
                27, 28 -> point.copy(y = 0.78f)
                else -> point
            }
        }
        assertTrue(PoseScoring.evaluate(YogaPose.SQUAT, BodyLandmarks(points)).score >= 0.68f)
    }

    @Test
    fun mirroringTranslationAndScaleDoNotChangeTheScore() {
        val body = squat()
        val expected = PoseScoring.evaluate(YogaPose.SQUAT, body).score
        for (scale in listOf(0.55f, 0.8f, 1f)) for (mirrored in listOf(false, true)) {
            val moved = BodyLandmarks(body.points.map { point ->
                point.copy(x = (if (mirrored) 1f - point.x else point.x) * scale + 0.05f,
                    y = point.y * scale + 0.05f)
            })
            assertEquals(expected, PoseScoring.evaluate(YogaPose.SQUAT, moved).score, 0.001f)
        }
    }

    @Test
    fun standingShallowBendsAndOneStraightLegDoNotCount() {
        val standing = squat().points.mapIndexed { index, point ->
            when (index) {
                23, 24 -> point.copy(y = 0.50f)
                25, 26 -> point.copy(y = 0.73f)
                else -> point
            }
        }
        val shallow = squat().points.mapIndexed { index, point ->
            if (index == 23 || index == 24) point.copy(y = 0.49f) else point
        }
        val oneStraightLeg = squat().points.mapIndexed { index, point ->
            if (index == 26) point.copy(y = 0.79f) else point
        }
        listOf(standing, shallow, oneStraightLeg).forEach { points ->
            assertTrue(PoseScoring.evaluate(YogaPose.SQUAT, BodyLandmarks(points)).score < 0.58f)
        }
    }

    @Test
    fun narrowKneesFloorSittingAndSidewaysLeanDoNotCount() {
        val narrow = squat().points.mapIndexed { index, point ->
            when (index) {
                25 -> point.copy(x = 0.43f)
                26 -> point.copy(x = 0.57f)
                else -> point
            }
        }
        val floor = squat().points.mapIndexed { index, point ->
            if (index == 23 || index == 24) point.copy(y = 0.86f) else point
        }
        val leaning = squat().points.mapIndexed { index, point ->
            if (index == 11 || index == 12) point.copy(x = point.x + 0.25f) else point
        }
        listOf(narrow, floor, leaning).forEach { points ->
            assertTrue(PoseScoring.evaluate(YogaPose.SQUAT, BodyLandmarks(points)).score < 0.58f)
        }
    }

    @Test
    fun overheadDroppedOrWideGoddessHandsDoNotCount() {
        for ((xOffset, y) in listOf(0f to 0.05f, 0f to 0.80f, 0.3f to 0.35f)) {
            val points = squat().points.mapIndexed { index, point ->
                when (index) {
                    15 -> point.copy(x = point.x - xOffset, y = y)
                    16 -> point.copy(x = point.x + xOffset, y = y)
                    else -> point
                }
            }
            assertTrue(PoseScoring.evaluate(YogaPose.SQUAT, BodyLandmarks(points)).score < 0.58f)
        }
    }

    @Test
    fun missingOrLowConfidenceBodyLandmarksOrBothWristsStopDetection() {
        for (missing in listOf(11, 12, 23, 24, 25, 26, 27, 28)) {
            for (lowPresence in listOf(false, true)) {
                val cropped = BodyLandmarks(squat().points.mapIndexed { index, point ->
                    if (index != missing) point
                    else if (lowPresence) point.copy(presence = 0.2f)
                    else point.copy(visibility = 0.2f)
                })
                assertFalse("Missing $missing presence=$lowPresence", PoseScoring.evaluate(YogaPose.SQUAT, cropped).framed)
            }
        }
        val noWrists = BodyLandmarks(squat().points.mapIndexed { index, point ->
            if (index == 15 || index == 16) point.copy(visibility = 0f) else point
        })
        assertFalse(PoseScoring.evaluate(YogaPose.SQUAT, noWrists).framed)
        assertFalse(PoseScoring.evaluate(YogaPose.SQUAT, null).framed)
        assertFalse(PoseScoring.evaluate(YogaPose.SQUAT, BodyLandmarks(emptyList())).framed)
    }
}
