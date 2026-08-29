package com.yogaalarm.prototype.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yogaalarm.prototype.model.AlarmConfig
import com.yogaalarm.prototype.model.AlarmSound
import com.yogaalarm.prototype.model.BodyLandmarks
import com.yogaalarm.prototype.model.PoseFrame
import com.yogaalarm.prototype.model.PoseScoring
import com.yogaalarm.prototype.model.YogaPose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class RoutinePhase { FINDING, HOLDING, PAUSED, TRANSITION, COMPLETE }

data class RoutineUiState(
    val landmarks: BodyLandmarks? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val phase: RoutinePhase = RoutinePhase.FINDING,
    val pose: YogaPose = YogaPose.MOUNTAIN,
    val poseIndex: Int = 0,
    val poseCount: Int = 3,
    val poseScore: Float = 0f,
    val framed: Boolean = false,
    val detected: Boolean = false,
    val remainingSeconds: Int = 20,
    val holdProgress: Float = 0f,
    val routineProgress: Float = 0f,
    val transitionSeconds: Int = 3,
    val alarmLevel: Float = 1f,
    val soundEnabled: Boolean = true,
    val alarmSound: AlarmSound = AlarmSound.MORNING_BELLS,
    val error: String? = null,
)

class RoutineViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = mutableUiState.asStateFlow()

    private var alarm = AlarmConfig.create()
    private var poseIndex = 0
    private var phase = RoutinePhase.FINDING
    private var holdMs = 0L
    private var detected = false
    private var latestScore = 0f
    private var latestFramed = false
    private var foundSinceMs: Long? = null
    private var lostSinceMs: Long? = null
    private var transitionEndsAtMs = 0L
    private var previousTickAtMs = 0L
    private var alarmLevel = 1f

    init {
        viewModelScope.launch {
            while (isActive) {
                tick(SystemClock.elapsedRealtime())
                delay(50)
            }
        }
    }

    fun start(config: AlarmConfig) {
        alarm = config
        poseIndex = 0
        phase = RoutinePhase.FINDING
        holdMs = 0L
        detected = false
        latestScore = 0f
        latestFramed = false
        foundSinceMs = null
        lostSinceMs = null
        alarmLevel = 1f
        previousTickAtMs = SystemClock.elapsedRealtime()
        publish()
    }

    fun onPoseFrame(frame: PoseFrame) {
        viewModelScope.launch {
            val evaluation = PoseScoring.evaluate(currentStep().pose, frame.landmarks)
            latestScore = evaluation.score
            latestFramed = evaluation.framed
            updateDetection(SystemClock.elapsedRealtime())
            mutableUiState.update {
                it.copy(
                    landmarks = frame.landmarks,
                    imageWidth = frame.imageWidth,
                    imageHeight = frame.imageHeight,
                    poseScore = latestScore,
                    framed = latestFramed,
                    detected = detected,
                    error = null,
                )
            }
        }
    }

    fun onCameraError(message: String) {
        mutableUiState.update { it.copy(error = message) }
    }

    private fun updateDetection(now: Long) {
        when {
            latestScore >= ENTER_SCORE -> {
                lostSinceMs = null
                if (foundSinceMs == null) foundSinceMs = now
                if (now - (foundSinceMs ?: now) >= ENTER_DELAY_MS) detected = true
            }
            latestScore < EXIT_SCORE -> {
                foundSinceMs = null
                if (lostSinceMs == null) lostSinceMs = now
                if (now - (lostSinceMs ?: now) >= EXIT_DELAY_MS) detected = false
            }
        }
    }

    private fun tick(now: Long) {
        val deltaMs = if (previousTickAtMs == 0L) 0L else (now - previousTickAtMs).coerceIn(0L, 100L)
        previousTickAtMs = now

        when (phase) {
            RoutinePhase.TRANSITION -> if (now >= transitionEndsAtMs) {
                poseIndex += 1
                holdMs = 0L
                detected = false
                foundSinceMs = null
                lostSinceMs = null
                latestScore = 0f
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

    private fun publish(now: Long = SystemClock.elapsedRealtime()) {
        val durationMs = currentDurationMs().coerceAtLeast(1L)
        val holdProgress = (holdMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val routineProgress = ((poseIndex + holdProgress) / alarm.routine.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        mutableUiState.update {
            it.copy(
                phase = phase,
                pose = currentStep().pose,
                poseIndex = poseIndex,
                poseCount = alarm.routine.size,
                poseScore = latestScore,
                framed = latestFramed,
                detected = detected,
                remainingSeconds = ceil((durationMs - holdMs).coerceAtLeast(0L) / 1_000f).toInt(),
                holdProgress = holdProgress,
                routineProgress = routineProgress,
                transitionSeconds = ceil((transitionEndsAtMs - now).coerceAtLeast(0L) / 1_000f).toInt().coerceAtLeast(1),
                alarmLevel = if (alarm.soundEnabled) alarmLevel else 0f,
                soundEnabled = alarm.soundEnabled,
                alarmSound = alarm.sound,
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
            RoutinePhase.HOLDING -> (1f - routineProgress * 0.85f).coerceAtLeast(0.15f)
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
        const val ENTER_SCORE = 0.74f
        const val EXIT_SCORE = 0.50f
        const val ENTER_DELAY_MS = 450L
        const val EXIT_DELAY_MS = 900L
        const val TRANSITION_MS = 3_000L
        const val VOLUME_RISE_PER_SECOND = 0.14f
        const val VOLUME_FALL_PER_SECOND = 0.75f
    }
}
