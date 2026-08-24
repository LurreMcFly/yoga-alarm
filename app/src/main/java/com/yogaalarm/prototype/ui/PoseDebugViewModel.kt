package com.yogaalarm.prototype.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.yogaalarm.prototype.model.BodyFraming
import com.yogaalarm.prototype.model.BodyLandmarks
import com.yogaalarm.prototype.model.PoseFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PoseDebugUiState(
    val landmarks: BodyLandmarks? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val inferenceTimeMs: Long? = null,
    val resultFps: Float = 0f,
    val isFullBodyVisible: Boolean = false,
    val missingLandmarks: List<String> = listOf("body"),
    val error: String? = null,
)

class PoseDebugViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(PoseDebugUiState())
    val uiState: StateFlow<PoseDebugUiState> = mutableUiState.asStateFlow()

    private var previousResultAtMs: Long? = null
    private var smoothedFps = 0f

    fun onPoseFrame(frame: PoseFrame) {
        val now = SystemClock.elapsedRealtime()
        previousResultAtMs?.let { previous ->
            val elapsedMs = now - previous
            if (elapsedMs > 0) {
                val instantaneousFps = 1_000f / elapsedMs
                smoothedFps = if (smoothedFps == 0f) {
                    instantaneousFps
                } else {
                    (smoothedFps * 0.8f) + (instantaneousFps * 0.2f)
                }
            }
        }
        previousResultAtMs = now

        val framing = BodyFraming.evaluate(frame.landmarks)
        mutableUiState.update {
            PoseDebugUiState(
                landmarks = frame.landmarks,
                imageWidth = frame.imageWidth,
                imageHeight = frame.imageHeight,
                inferenceTimeMs = frame.inferenceTimeMs,
                resultFps = smoothedFps,
                isFullBodyVisible = framing.isFullBodyVisible,
                missingLandmarks = framing.missingLandmarks,
            )
        }
    }

    fun onCameraError(message: String) {
        mutableUiState.update { it.copy(error = message) }
    }
}
