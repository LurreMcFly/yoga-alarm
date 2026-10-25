package com.lurremcfly.yogaalarm.model

import java.time.LocalTime

enum class YogaPose(
    val displayName: String,
    val isFree: Boolean,
) {
    MOUNTAIN("Mountain", true),
    WARRIOR_TWO("Warrior II", true),
    TREE("Tree", true),
    SQUAT("Squat", true),
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
    val stopButtonDisabled: Boolean,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val snoozeCount: Int,
) : java.io.Serializable {
    companion object {
        private val morningNames = listOf(
            "Rise & stretch ☀️", "Morning movement", "A fresh start 🌱", "Wake up gently",
            "Hello, new day 🌤️", "Start with a stretch", "Meet the morning 🌅", "Your morning moment",
        )
        private val middayNames = listOf(
            "Midday reset 🌿", "A little movement", "Find your balance ⚖️", "Stretch break",
            "Pause & stretch 🧘", "Make space for you", "Refresh & refocus ✨", "Time to move",
        )
        private val eveningNames = listOf(
            "Evening unwind 🌙", "Release the day", "Sunset stretch 🌅", "Room to breathe",
            "Let the day go 🍃", "Ease into evening", "A softer pace 🌿", "Evening balance",
        )
        private val nightNames = listOf(
            "Moonlight movement 🌙", "Quiet reset", "Night owl stretch 🦉", "Move at your pace",
            "A mindful moment ✨", "Quiet strength", "Under the stars 🌌", "A gentle stretch",
        )

        internal fun defaultName(hour: Int, existingNames: Collection<String> = emptyList()): String {
            val names = when (hour) {
                in 5..10 -> morningNames
                in 11..16 -> middayNames
                in 17..21 -> eveningNames
                else -> nightNames
            }
            return names.filterNot { it in existingNames }.ifEmpty { names }.random()
        }

        fun create(
            id: Long = System.currentTimeMillis(),
            now: LocalTime = LocalTime.now(),
            existingNames: Collection<String> = emptyList(),
        ) = AlarmConfig(
            id = id,
            name = defaultName(now.hour, existingNames),
            hour = now.hour,
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
            stopButtonDisabled = false,
            snoozeEnabled = true,
            snoozeMinutes = 5,
            snoozeCount = 1,
        )
    }
}
