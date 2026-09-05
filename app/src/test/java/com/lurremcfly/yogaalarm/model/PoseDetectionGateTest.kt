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
        assertTrue(gate.update(score = 0.2f, nowMs = 2_300L))
        assertTrue(gate.update(score = 0.8f, nowMs = 2_400L))
    }

    @Test
    fun sustainedTrackingLossDropsAcceptedPose() {
        val gate = enteredGate()

        assertTrue(gate.update(score = 0.2f, nowMs = 2_000L))
        assertFalse(gate.update(score = 0.2f, nowMs = 2_450L))
    }

    @Test
    fun hysteresisBandPreservesCurrentState() {
        val gate = enteredGate()

        assertTrue(gate.update(score = 0.62f, nowMs = 10_000L))
        assertFalse(PoseDetectionGate().update(score = 0.62f, nowMs = 10_000L))
    }

    @Test
    fun partialPoseNoLongerKeepsHoldAccepted() {
        val gate = enteredGate()
        assertTrue(gate.update(score = 0.5f, nowMs = 2_000L))
        assertFalse(gate.update(score = 0.5f, nowMs = 2_450L))
    }

    @Test
    fun entryRequiresConsecutiveHighScores() {
        val gate = PoseDetectionGate()
        assertFalse(gate.update(score = 0.8f, nowMs = 1_000L))
        assertFalse(gate.update(score = 0.62f, nowMs = 1_200L))
        assertFalse(gate.update(score = 0.8f, nowMs = 1_300L))
        assertTrue(gate.update(score = 0.8f, nowMs = 1_600L))
    }

    @Test
    fun recoveryIntoHysteresisBandRestartsLossDelay() {
        val gate = enteredGate()
        assertTrue(gate.update(score = 0.2f, nowMs = 2_000L))
        assertTrue(gate.update(score = 0.62f, nowMs = 2_300L))
        assertTrue(gate.update(score = 0.2f, nowMs = 2_500L))
        assertFalse(gate.update(score = 0.2f, nowMs = 2_950L))
    }

    private fun enteredGate() = PoseDetectionGate().apply {
        update(score = 0.8f, nowMs = 1_000L)
        update(score = 0.8f, nowMs = 1_300L)
    }
}
