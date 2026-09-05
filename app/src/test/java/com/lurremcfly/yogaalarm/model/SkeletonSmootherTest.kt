package com.lurremcfly.yogaalarm.model

import org.junit.Assert.*
import org.junit.Test

class SkeletonSmootherTest {
    private fun frame(time: Long, kneeX: Float = 0.4f, visibility: Float = 1f): PoseFrame {
        val points = MutableList(33) { BodyPoint(0.5f, 0.5f, 0f, 1f, 1f) }
        points[11] = points[11].copy(x = 0.4f, y = 0.3f)
        points[12] = points[12].copy(x = 0.6f, y = 0.3f)
        points[23] = points[23].copy(x = 0.4f, y = 0.6f)
        points[24] = points[24].copy(x = 0.6f, y = 0.6f)
        points[25] = points[25].copy(x = kneeX, y = 0.8f, visibility = visibility)
        return PoseFrame(BodyLandmarks(points), 10, 480, 640, time)
    }

    @Test
    fun smallJitterIsSmoothedWithoutChangingInput() {
        val smoother = SkeletonSmoother()
        smoother.smooth(frame(1_000))
        val raw = frame(1_067, 0.44f)
        val filtered = smoother.smooth(raw).landmarks!![25]!!
        assertTrue(filtered.x > 0.4f && filtered.x < 0.44f)
        assertEquals(0.44f, raw.landmarks!![25]!!.x, 0f)
    }

    @Test
    fun oneFrameLegJumpIsRejectedAndRealMovementIsConfirmed() {
        val smoother = SkeletonSmoother()
        smoother.smooth(frame(1_000))
        assertEquals(0.4f, smoother.smooth(frame(1_067, 0.85f)).landmarks!![25]!!.x, 0f)
        assertEquals(0.4f, smoother.smooth(frame(1_134)).landmarks!![25]!!.x, 0f)
        assertEquals(0.4f, smoother.smooth(frame(1_201, 0.85f)).landmarks!![25]!!.x, 0f)
        assertEquals(0.84f, smoother.smooth(frame(1_268, 0.84f)).landmarks!![25]!!.x, 0f)
    }

    @Test
    fun unconfirmedJumpsExpireRatherThanLeavingAGhostLeg() {
        val smoother = SkeletonSmoother()
        smoother.smooth(frame(1_000))
        smoother.smooth(frame(1_067, 0.85f))
        val unstable = smoother.smooth(frame(1_134, 0.02f)).landmarks!![25]!!
        assertEquals(0f, unstable.visibility, 0f)
    }

    @Test
    fun missingLandmarksAndLowConfidenceAreNeverHeldOnScreen() {
        val smoother = SkeletonSmoother()
        smoother.smooth(frame(1_000))
        assertEquals(0f, smoother.smooth(frame(1_067, visibility = 0.2f)).landmarks!![25]!!.visibility, 0f)
        assertNull(smoother.smooth(frame(1_134).copy(landmarks = null)).landmarks)
        assertEquals(0.85f, smoother.smooth(frame(1_201, 0.85f)).landmarks!![25]!!.x, 0f)
    }

    @Test
    fun cameraRestartAndFrameGapsClearHistory() {
        val smoother = SkeletonSmoother()
        smoother.smooth(frame(1_000))
        assertEquals(0.85f, smoother.smooth(frame(2_000, 0.85f)).landmarks!![25]!!.x, 0f)
        smoother.reset()
        assertEquals(0.1f, smoother.smooth(frame(2_067, 0.1f)).landmarks!![25]!!.x, 0f)
    }

    @Test
    fun timeBasedSmoothingHasTheSameResponseAtDifferentFrameRates() {
        fun response(interval: Long): Float {
            val smoother = SkeletonSmoother()
            smoother.smooth(frame(1_000))
            var result = 0f
            for (time in interval..240L step interval) result = smoother.smooth(frame(1_000 + time, 0.44f)).landmarks!![25]!!.x
            return result
        }
        assertEquals(response(30), response(60), 0.0001f)
    }
}
