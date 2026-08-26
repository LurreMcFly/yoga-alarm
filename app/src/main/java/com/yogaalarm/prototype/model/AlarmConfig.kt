package com.yogaalarm.prototype.model

enum class YogaPose(
    val displayName: String,
    val isFree: Boolean,
) {
    MOUNTAIN("Mountain", true),
    WARRIOR_TWO("Warrior II", true),
    TREE("Tree", true),
    CHAIR("Chair", false),
    FORWARD_FOLD("Forward Fold", false),
    TRIANGLE("Triangle", false),
    GODDESS("Goddess", false),
    WIDE_LEG_FOLD("Wide-Leg Fold", false),
}

data class PoseStep(
    val pose: YogaPose,
    val durationSeconds: Int = 20,
)

data class AlarmConfig(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val weekdays: Set<Int>,
    val enabled: Boolean,
    val routine: List<PoseStep>,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val snoozeEnabled: Boolean,
) {
    companion object {
        fun create(id: Long = System.currentTimeMillis()) = AlarmConfig(
            id = id,
            name = "Morning movement",
            hour = 7,
            minute = 0,
            weekdays = setOf(1, 2, 3, 4, 5),
            enabled = true,
            routine = listOf(
                PoseStep(YogaPose.MOUNTAIN),
                PoseStep(YogaPose.WARRIOR_TWO),
                PoseStep(YogaPose.TREE),
            ),
            soundEnabled = true,
            vibrationEnabled = true,
            snoozeEnabled = true,
        )
    }
}
