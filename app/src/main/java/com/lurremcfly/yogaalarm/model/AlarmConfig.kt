package com.lurremcfly.yogaalarm.model

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

enum class AlarmSound(
    val displayName: String,
    val description: String,
    val isFree: Boolean,
) {
    SUNBIRD_MORNING_CALL("Sunbird Morning Call", "Bright natural bird call", true),
    MORNING_TEMPLE_CALL("Morning Temple Call", "Calm resonant morning bells", true),
    LOTUS_SUNRISE_LOOP("Lotus Sunrise Loop", "Warm meditative loop", false),
    DIZI_DAWN_ALARM("Dizi Dawn Alarm", "Airy flute awakening", false),
    SILK_ROAD_SUNRISE("Silk Road Sunrise", "Gentle cinematic sunrise", false),
    SUNRISE_CIRCLE("Sunrise Circle", "Soft rhythmic morning loop", false),
    BAMBOO_DAWN_BELL("Bamboo Dawn Bell", "Light bamboo and bell tones", false),
    MORNING_BELL_RUN("Morning Bell Run", "Clear flowing bell pattern", false),
    SUNRISE_FLOW_LOOP("Sunrise Flow Loop", "Smooth peaceful wake-up", false),
    RICE_PAPER_DAWN("Rice Paper Dawn", "Slow atmospheric morning", false),
    ;

    companion object {
        fun fromStoredName(name: String?): AlarmSound = when (name) {
            "MORNING_BELLS" -> MORNING_TEMPLE_CALL
            "NATURE_BIRDS" -> SUNBIRD_MORNING_CALL
            else -> entries.firstOrNull { it.name == name } ?: SUNBIRD_MORNING_CALL
        }
    }
}

data class PoseStep(
    val pose: YogaPose,
    val durationSeconds: Int = 20,
) : java.io.Serializable

data class AlarmConfig(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val weekdays: Set<Int>,
    val enabled: Boolean,
    val routine: List<PoseStep>,
    val sound: AlarmSound,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val snoozeCount: Int,
) : java.io.Serializable {
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
            sound = AlarmSound.SUNBIRD_MORNING_CALL,
            soundEnabled = true,
            vibrationEnabled = true,
            snoozeEnabled = true,
            snoozeMinutes = 5,
            snoozeCount = 1,
        )
    }
}
