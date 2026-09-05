package com.lurremcfly.yogaalarm.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewProjectionTest {
    @Test
    fun portraitPreviewFillsHeightAndCropsSidesSymmetrically() {
        val projection = PreviewProjection(PoseFrame(null, 0, 480, 640), 360f, 800f)
        assertEquals(180f, projection.x(0.5f), 0.001f)
        assertEquals(400f, projection.y(0.5f), 0.001f)
        assertEquals(-120f, projection.x(0f), 0.001f)
        assertEquals(0f, projection.y(0f), 0.001f)
        assertEquals(800f, projection.y(1f), 0.001f)
    }

    @Test
    fun sharedViewportMapsCropEdgesToPreviewEdges() {
        val frame = PoseFrame(null, 0, 480, 640, cropLeft = 0.2f, cropRight = 0.8f)
        val projection = PreviewProjection(frame, 360f, 800f)
        assertEquals(0f, projection.x(0.2f), 0.001f)
        assertEquals(360f, projection.x(0.8f), 0.001f)
        assertEquals(400f, projection.y(0.5f), 0.001f)
    }

    @Test
    fun offCenterCropUsesItsOwnCenterRatherThanTheFullImageCenter() {
        val frame = PoseFrame(null, 0, 1000, 1000, cropLeft = 0.1f, cropTop = 0.2f, cropRight = 0.5f, cropBottom = 1f)
        val projection = PreviewProjection(frame, 400f, 800f)
        assertEquals(0f, projection.x(0.1f), 0.001f)
        assertEquals(400f, projection.x(0.5f), 0.001f)
        assertEquals(0f, projection.y(0.2f), 0.001f)
        assertEquals(800f, projection.y(1f), 0.001f)
    }

    @Test
    fun landscapePreviewFillsWidthAndAlreadyMirroredPointsStayMirrored() {
        val projection = PreviewProjection(PoseFrame(null, 0, 640, 480), 800f, 360f)
        assertEquals(0f, projection.x(0f), 0.001f)
        assertEquals(800f, projection.x(1f), 0.001f)
        assertEquals(-120f, projection.y(0f), 0.001f)
        assertEquals(180f, projection.y(0.5f), 0.001f)
    }
}
