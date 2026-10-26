package com.lurremcfly.yogaalarm.model

import org.junit.Assert.*
import org.junit.Test

class SidePoseScoringTest {
    private fun profile(vararg coords: Pair<Int, Pair<Float, Float>>): BodyLandmarks {
        val points = MutableList(33) { BodyPoint(0f, 0f, 0f, 0f, 0f) }
        coords.forEach { (index, xy) -> points[index] = BodyPoint(xy.first, xy.second, 0f, 1f, 1f) }
        return BodyLandmarks(points)
    }

    private fun chair() = profile(
        11 to (0.48f to 0.35f), 13 to (0.38f to 0.23f), 15 to (0.28f to 0.11f),
        23 to (0.58f to 0.60f), 25 to (0.40f to 0.75f), 27 to (0.48f to 0.95f),
    )

    private fun seatedFold() = profile(
        11 to (0.42f to 0.57f), 13 to (0.58f to 0.63f), 15 to (0.75f to 0.69f),
        23 to (0.27f to 0.80f), 25 to (0.57f to 0.82f), 27 to (0.88f to 0.84f),
    )

    @Test
    fun sideViewsWorkWithEitherSideOccludedAndEitherFacingDirection() {
        listOf(YogaPose.CHAIR to chair(), YogaPose.FORWARD_FOLD to seatedFold()).forEach { (pose, body) ->
            for (side in 0..1) for (mirrored in listOf(false, true)) {
                val points = MutableList(33) { BodyPoint(0f, 0f, 0f, 0f, 0f) }
                listOf(11, 13, 15, 23, 25, 27).forEach { index ->
                    val point = body.points[index]
                    points[index + side] = point.copy(x = if (mirrored) 1f - point.x else point.x)
                }
                val evaluation = PoseScoring.evaluate(pose, BodyLandmarks(points))
                assertTrue("$pose side=$side mirrored=$mirrored score=${evaluation.score}", evaluation.score >= 0.68f)
                assertTrue(evaluation.framed)
            }
        }
    }

    @Test
    fun standingWithRaisedArmsIsNotChair() {
        val body = chair().points.toMutableList()
        body[23] = body[23].copy(x = 0.48f, y = 0.6f)
        body[25] = body[25].copy(x = 0.48f, y = 0.77f)
        assertTrue(PoseScoring.evaluate(YogaPose.CHAIR, BodyLandmarks(body)).score < 0.58f)
    }

    @Test
    fun seatedUprightOrReachingBackwardsIsNotForwardFold() {
        val upright = seatedFold().points.toMutableList()
        upright[11] = upright[11].copy(x = 0.27f)
        assertTrue(PoseScoring.evaluate(YogaPose.FORWARD_FOLD, BodyLandmarks(upright)).score < 0.58f)
        val backwards = seatedFold().points.toMutableList()
        backwards[15] = backwards[15].copy(x = 0.20f)
        assertTrue(PoseScoring.evaluate(YogaPose.FORWARD_FOLD, BodyLandmarks(backwards)).score < 0.58f)
    }

    @Test
    fun missingBothFeetDoesNotAcceptASidePose() {
        listOf(YogaPose.CHAIR to chair(), YogaPose.FORWARD_FOLD to seatedFold()).forEach { (pose, body) ->
            val cropped = BodyLandmarks(body.points.mapIndexed { index, point -> if (index == 27) point.copy(visibility = 0f) else point })
            assertFalse(PoseScoring.evaluate(pose, cropped).framed)
        }
    }

    private fun wideFold() = profile(
        11 to (0.40f to 0.51f), 12 to (0.60f to 0.51f),
        15 to (0.40f to 0.84f), 16 to (0.60f to 0.84f),
        23 to (0.43f to 0.54f), 24 to (0.57f to 0.54f),
        25 to (0.29f to 0.73f), 26 to (0.71f to 0.73f),
        27 to (0.15f to 0.92f), 28 to (0.85f to 0.92f),
    )

    @Test
    fun wideFoldAcceptsForeshortenedTorsoAndOccludedHead() {
        assertTrue(PoseScoring.evaluate(YogaPose.WIDE_LEG_FOLD, wideFold()).score >= 0.68f)
        val deeper = wideFold().points.toMutableList()
        deeper[11] = deeper[11].copy(y = 0.72f)
        deeper[12] = deeper[12].copy(y = 0.72f)
        assertTrue(PoseScoring.evaluate(YogaPose.WIDE_LEG_FOLD, BodyLandmarks(deeper)).score >= 0.68f)
    }

    @Test
    fun standingWideIsNotFoldAndPositionDoesNotAffectScore() {
        val standing = wideFold().points.toMutableList()
        standing[11] = standing[11].copy(y = 0.18f)
        standing[12] = standing[12].copy(y = 0.18f)
        assertTrue(PoseScoring.evaluate(YogaPose.WIDE_LEG_FOLD, BodyLandmarks(standing)).score < 0.58f)
        val moved = BodyLandmarks(wideFold().points.map { it.copy(x = it.x * 0.7f + 0.1f, y = it.y * 0.7f + 0.2f) })
        assertEquals(PoseScoring.evaluate(YogaPose.WIDE_LEG_FOLD, wideFold()).score,
            PoseScoring.evaluate(YogaPose.WIDE_LEG_FOLD, moved).score, 0.001f)
    }
}
