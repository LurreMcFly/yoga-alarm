package com.lurremcfly.yogaalarm.model

class PoseDetectionGate(
    private val enterScore: Float = 0.68f,
    private val exitScore: Float = 0.36f,
    private val enterDelayMs: Long = 300L,
    private val exitDelayMs: Long = 1_800L,
) {
    var detected: Boolean = false
        private set

    private var foundSinceMs: Long? = null
    private var lostSinceMs: Long? = null

    fun update(score: Float, nowMs: Long): Boolean {
        when {
            score >= enterScore -> {
                lostSinceMs = null
                if (foundSinceMs == null) foundSinceMs = nowMs
                if (nowMs - (foundSinceMs ?: nowMs) >= enterDelayMs) detected = true
            }
            score < exitScore -> {
                foundSinceMs = null
                if (lostSinceMs == null) lostSinceMs = nowMs
                if (nowMs - (lostSinceMs ?: nowMs) >= exitDelayMs) detected = false
            }
        }
        return detected
    }

    fun reset() {
        detected = false
        foundSinceMs = null
        lostSinceMs = null
    }
}
