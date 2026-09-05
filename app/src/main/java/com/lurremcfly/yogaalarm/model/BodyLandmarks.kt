package com.lurremcfly.yogaalarm.model

data class BodyPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float,
)

data class BodyLandmarks(
    val points: List<BodyPoint>,
) {
    operator fun get(index: Int): BodyPoint? = points.getOrNull(index)
}

data class FramingResult(
    val isFullBodyVisible: Boolean,
    val missingLandmarks: List<String>,
)

object BodyFraming {
    private const val MIN_CONFIDENCE = 0.6f
    private const val FRAME_MARGIN = 0.02f

    private val requiredLandmarks = listOf(
        NamedLandmark("head", 0),
        NamedLandmark("left shoulder", 11),
        NamedLandmark("right shoulder", 12),
        NamedLandmark("left hip", 23),
        NamedLandmark("right hip", 24),
        NamedLandmark("left knee", 25),
        NamedLandmark("right knee", 26),
        NamedLandmark("left ankle", 27),
        NamedLandmark("right ankle", 28),
    )

    fun evaluate(landmarks: BodyLandmarks?): FramingResult {
        if (landmarks == null) {
            return FramingResult(
                isFullBodyVisible = false,
                missingLandmarks = listOf("body"),
            )
        }

        val missing = requiredLandmarks.mapNotNull { required ->
            val point = landmarks[required.index]
            if (point == null || !point.isUsable()) required.name else null
        }

        return FramingResult(
            isFullBodyVisible = missing.isEmpty(),
            missingLandmarks = missing,
        )
    }

    private fun BodyPoint.isUsable(): Boolean =
        visibility >= MIN_CONFIDENCE &&
            presence >= MIN_CONFIDENCE &&
            x in FRAME_MARGIN..(1f - FRAME_MARGIN) &&
            y in FRAME_MARGIN..(1f - FRAME_MARGIN)

    private data class NamedLandmark(
        val name: String,
        val index: Int,
    )
}
