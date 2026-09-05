package com.lurremcfly.yogaalarm.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseDetectionGateTest {
    @Test
    fun requiresStablePoseBeforeEntering() {
        val gate = PoseDetectionGate()

        assertFalse(gate.update(score = 0.8f, nowMs = 1_000L))
        assertFalse(gate.update(score = 0.8f, nowMs = 1_299L))
        assertTrue(gate.update(score = 0.8f, nowMs = 1_300L))
    }

    @Test
    fun briefTrackingLossDoesNotDropAcceptedPose() {
        val gate = enteredGate()

        assertTrue(gate.update(score = 0.2f, nowMs = 2_000L))
        assertTrue(gate.update(score = 0.2f, nowMs = 3_799L))
        assertTrue(gate.update(score = 0.8f, nowMs = 3_800L))
    }

    @Test
    fun sustainedTrackingLossDropsAcceptedPose() {
        val gate = enteredGate()

        assertTrue(gate.update(score = 0.2f, nowMs = 2_000L))
        assertFalse(gate.update(score = 0.2f, nowMs = 3_800L))
    }

    @Test
    fun hysteresisBandPreservesCurrentState() {
        val gate = enteredGate()

        assertTrue(gate.update(score = 0.5f, nowMs = 10_000L))
    }

    private fun enteredGate() = PoseDetectionGate().apply {
        update(score = 0.8f, nowMs = 1_000L)
        update(score = 0.8f, nowMs = 1_300L)
    }
}
