package com.lurremcfly.yogaalarm.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmSound
import com.lurremcfly.yogaalarm.model.PoseFrame
import com.lurremcfly.yogaalarm.model.PoseDetectionGate
import com.lurremcfly.yogaalarm.model.PoseScoring
import com.lurremcfly.yogaalarm.model.SkeletonSmoother
import com.lurremcfly.yogaalarm.model.YogaPose
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.pow

enum class RoutinePhase { FINDING, HOLDING, PAUSED, TRANSITION, COMPLETE }

data class RoutineUiState(
    val started: Boolean = false,
    val cameraReady: Boolean = false,
    val phase: RoutinePhase = RoutinePhase.FINDING,
    val pose: YogaPose = YogaPose.MOUNTAIN,
    val nextPose: YogaPose? = null,
    val poseIndex: Int = 0,
    val poseCount: Int = 3,
    val almostThere: Boolean = false,
    val framingHint: String = "Position yourself in the camera view",
    val framed: Boolean = false,
    val detected: Boolean = false,
    val remainingSeconds: Int = 20,
    val transitionSeconds: Int = 3,
    val soundEnabled: Boolean = true,
    val alarmSound: AlarmSound = AlarmSound.SUNBIRD_MORNING_CALL,
    val alarmHour: Int = 7,
    val error: String? = null,
)

data class RoutineProgress(val hold: Float = 0f, val routine: Float = 0f, val alarmLevel: Float = 1f)

class RoutineViewModel(private val nowMs: () -> Long = SystemClock::elapsedRealtime) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = mutableUiState.asStateFlow()
    private val mutableFrames = MutableStateFlow<PoseFrame?>(null)
    val frames = mutableFrames.asStateFlow()
    private val mutableProgress = MutableStateFlow(RoutineProgress())
    val progress = mutableProgress.asStateFlow()
    private var ticker: Job? = null
    private var sessionActive = false
    private var lastFrameAtMs: Long? = null
    private var lastScoreAtMs: Long? = null
    private var cameraReady = false
    private var waitingForCameraSinceMs = 0L
    private var framingHint = "Position yourself in the camera view"

    private var alarm = AlarmConfig.create()
    private var started = false
    private var poseIndex = 0
    private var phase = RoutinePhase.FINDING
    private var holdMs = 0L
    private var detected = false
    private val detectionGate = PoseDetectionGate()
    private val skeletonSmoother = SkeletonSmoother()
    private var latestScore = 0f
    private var latestFramed = false
    private var framingLostSinceMs: Long? = null
    private var transitionEndsAtMs = 0L
    private var previousTickAtMs = 0L
    private var alarmLevel = 1f

    fun start(config: AlarmConfig) {
        if (started && alarm == config) {
            resume()
            return
        }
        ticker?.cancel()
        skeletonSmoother.reset()
        alarm = config
        started = true
        poseIndex = 0
        phase = RoutinePhase.FINDING
        holdMs = 0L
        detected = false
        latestScore = 0f
        latestFramed = false
        framingLostSinceMs = null
        detectionGate.reset()
        alarmLevel = 1f
        lastFrameAtMs = null
        lastScoreAtMs = null
        cameraReady = false
        waitingForCameraSinceMs = nowMs()
        mutableFrames.value = null
        mutableUiState.value = RoutineUiState()
        previousTickAtMs = nowMs()
        publish()
        resume()
    }

    fun resume() {
        if (!started || phase == RoutinePhase.COMPLETE || ticker?.isActive == true) return
        sessionActive = true
        previousTickAtMs = nowMs()
        ticker = viewModelScope.launch {
            while (isActive && phase != RoutinePhase.COMPLETE) {
                tick(nowMs())
                delay(50)
            }
        }
    }

    fun pause() {
        sessionActive = false
        ticker?.cancel()
        ticker = null
        resetTracking()
        if (phase != RoutinePhase.COMPLETE && phase != RoutinePhase.TRANSITION) {
            phase = if (holdMs > 0) RoutinePhase.PAUSED else RoutinePhase.FINDING
        }
        publish()
    }

    fun stop() {
        pause()
        started = false
        phase = RoutinePhase.FINDING
        poseIndex = 0
        holdMs = 0L
        alarmLevel = 1f
        mutableUiState.value = RoutineUiState()
        mutableFrames.value = null
        publish()
    }

    fun onPoseFrame(frame: PoseFrame) {
        if (sessionActive && phase != RoutinePhase.COMPLETE) {
            val now = nowMs()
            val capturedAt = frame.capturedAtMs.takeIf { it > 0 } ?: now
            if (now - capturedAt > FRAME_TIMEOUT_MS) return
            lastFrameAtMs = capturedAt
            cameraReady = true
            mutableFrames.value = skeletonSmoother.smooth(frame.copy(capturedAtMs = capturedAt))
            mutableUiState.update { it.copy(error = null) }
            if (phase == RoutinePhase.TRANSITION) return
            val evaluation = PoseScoring.evaluate(currentStep().pose, frame.landmarks)
            val elapsed = (now - (lastScoreAtMs ?: (now - 33L))).coerceIn(1L, FRAME_TIMEOUT_MS)
            lastScoreAtMs = now
            val base = if (evaluation.score >= latestScore) SCORE_RISE_SMOOTHING else SCORE_FALL_SMOOTHING
            val smoothing = 1f - (1f - base).pow(elapsed / (1_000f / 30f))
            latestScore += (evaluation.score - latestScore) * smoothing
            if (evaluation.framed) {
                latestFramed = true
                framingLostSinceMs = null
            } else {
                if (framingLostSinceMs == null) framingLostSinceMs = now
                if (now - (framingLostSinceMs ?: now) >= FRAMING_GRACE_MS) latestFramed = false
            }
            framingHint = PoseScoring.framingHint(currentStep().pose, frame.landmarks)
            updateDetection(now)
        }
    }

    fun onCameraError(message: String) {
        resetTracking()
        if (phase != RoutinePhase.COMPLETE && phase != RoutinePhase.TRANSITION) {
            phase = if (holdMs > 0) RoutinePhase.PAUSED else RoutinePhase.FINDING
        }
        mutableUiState.update { it.copy(error = message) }
        publish()
    }

    fun onCameraRetry() {
        resetTracking()
        mutableUiState.update { it.copy(error = null) }
        publish()
    }

    private fun resetTracking() {
        skeletonSmoother.reset()
        detected = false
        detectionGate.reset()
        latestScore = 0f
        latestFramed = false
        framingLostSinceMs = null
        lastFrameAtMs = null
        lastScoreAtMs = null
        cameraReady = false
        waitingForCameraSinceMs = nowMs()
        mutableFrames.value = null
    }

    private fun updateDetection(now: Long) {
        if (!latestFramed) {
            detectionGate.reset()
            detected = false
            return
        }
        detected = detectionGate.update(latestScore, now)
    }

    private fun tick(now: Long) {
        if (!started) return
        val deltaMs = if (previousTickAtMs == 0L) 0L else (now - previousTickAtMs).coerceIn(0L, 100L)
        previousTickAtMs = now
        if (lastFrameAtMs?.let { now - it > FRAME_TIMEOUT_MS } == true) {
            resetTracking()
            mutableUiState.update { it.copy(error = "Camera tracking stopped. Try the camera again.") }
        }
        if (!cameraReady && mutableUiState.value.error == null && now - waitingForCameraSinceMs >= CAMERA_START_TIMEOUT_MS) {
            mutableUiState.update { it.copy(error = "The camera didn’t start. Try the camera again.") }
        }

        when (phase) {
            RoutinePhase.TRANSITION -> if (now >= transitionEndsAtMs) {
                poseIndex += 1
                holdMs = 0L
                detected = false
                detectionGate.reset()
                latestScore = 0f
                latestFramed = false
                framingLostSinceMs = null
                lastScoreAtMs = null
                phase = RoutinePhase.FINDING
            }
            RoutinePhase.FINDING, RoutinePhase.HOLDING, RoutinePhase.PAUSED -> {
                if (detected) {
                    phase = RoutinePhase.HOLDING
                    holdMs = (holdMs + deltaMs).coerceAtMost(currentDurationMs())
                    if (holdMs >= currentDurationMs()) {
                        if (poseIndex == alarm.routine.lastIndex) {
                            phase = RoutinePhase.COMPLETE
                        } else {
                            phase = RoutinePhase.TRANSITION
                            transitionEndsAtMs = now + TRANSITION_MS
                        }
                    }
                } else {
                    phase = if (holdMs > 0L) RoutinePhase.PAUSED else RoutinePhase.FINDING
                }
            }
            RoutinePhase.COMPLETE -> Unit
        }
        updateAlarmLevel(deltaMs)
        publish(now)
    }

    private fun publish(now: Long = nowMs()) {
        val durationMs = currentDurationMs().coerceAtLeast(1L)
        val holdProgress = (holdMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val routineProgress = ((poseIndex + holdProgress) / alarm.routine.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        mutableProgress.value = RoutineProgress(holdProgress, routineProgress, if (alarm.soundEnabled) alarmLevel else 0f)
        mutableUiState.update {
            it.copy(
                started = started,
                cameraReady = cameraReady,
                phase = phase,
                pose = currentStep().pose,
                nextPose = alarm.routine.getOrNull(poseIndex + 1)?.pose,
                poseIndex = poseIndex,
                poseCount = alarm.routine.size,
                almostThere = latestScore >= 0.55f,
                framingHint = framingHint,
                framed = latestFramed,
                detected = detected,
                remainingSeconds = ceil((durationMs - holdMs).coerceAtLeast(0L) / 1_000f).toInt(),
                transitionSeconds = ceil((transitionEndsAtMs - now).coerceAtLeast(0L) / 1_000f).toInt().coerceAtLeast(1),
                soundEnabled = alarm.soundEnabled,
                alarmSound = alarm.sound,
                alarmHour = alarm.hour,
            )
        }
    }

    private fun currentStep() = alarm.routine.getOrElse(poseIndex) { alarm.routine.first() }
    private fun currentDurationMs() = currentStep().durationSeconds * 1_000L

    private fun updateAlarmLevel(deltaMs: Long) {
        if (phase == RoutinePhase.COMPLETE) {
            alarmLevel = 0f
            return
        }
        val holdProgress = (holdMs.toFloat() / currentDurationMs().coerceAtLeast(1L)).coerceIn(0f, 1f)
        val routineProgress = ((poseIndex + holdProgress) / alarm.routine.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        val target = when (phase) {
            RoutinePhase.TRANSITION -> alarmLevel
            RoutinePhase.HOLDING -> {
                MIN_HOLDING_VOLUME +
                    (1f - MIN_HOLDING_VOLUME) * (1f - routineProgress).pow(VOLUME_FADE_CURVE)
            }
            RoutinePhase.PAUSED -> 1f
            RoutinePhase.FINDING -> alarmLevel
            RoutinePhase.COMPLETE -> 0f
        }
        val ratePerSecond = if (target > alarmLevel) VOLUME_RISE_PER_SECOND else VOLUME_FALL_PER_SECOND
        val step = ratePerSecond * deltaMs / 1_000f
        alarmLevel = if (target > alarmLevel) {
            (alarmLevel + step).coerceAtMost(target)
        } else {
            (alarmLevel - step).coerceAtLeast(target)
        }
    }

    private companion object {
        const val FRAME_TIMEOUT_MS = 900L
        const val CAMERA_START_TIMEOUT_MS = 10_000L
        const val FRAMING_GRACE_MS = 350L
        const val SCORE_RISE_SMOOTHING = 0.28f
        const val SCORE_FALL_SMOOTHING = 0.3f
        const val TRANSITION_MS = 3_000L
        const val MIN_HOLDING_VOLUME = 0.05f
        const val VOLUME_FADE_CURVE = 2.5f
        const val VOLUME_RISE_PER_SECOND = 0.14f
        const val VOLUME_FALL_PER_SECOND = 0.75f
    }
}
