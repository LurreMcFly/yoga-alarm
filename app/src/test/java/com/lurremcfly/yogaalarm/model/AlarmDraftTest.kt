package com.lurremcfly.yogaalarm.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDraftTest {
    @Test
    fun stopButtonIsVisibleByDefault() {
        assertFalse(AlarmConfig.create().stopButtonDisabled)
    }

    @Test
    fun newAlarmUsesTheCurrentLocalHourWithZeroMinutes() {
        for (hour in 0..23) {
            val alarm = AlarmConfig.create(now = LocalTime.of(hour, 37, 42))
            assertEquals(hour, alarm.hour)
            assertEquals(0, alarm.minute)
        }
    }

    @Test
    fun namesMatchTimeOfDayAndPreferUnusedNames() {
        val periods = listOf(
            (5..10).toList() to "Morning movement",
            (11..16).toList() to "Time to move",
            (17..21).toList() to "Evening balance",
            listOf(22, 23, 0, 1, 2, 3, 4) to "Quiet reset",
        )
        val allNames = mutableSetOf<String>()
        for ((hours, exampleName) in periods) {
            val names = mutableSetOf<String>()
            repeat(8) {
                val alarm = AlarmConfig.create(now = LocalTime.of(hours.first(), 0), existingNames = names)
                assertTrue("Prefer an unused name", names.add(alarm.name))
                assertTrue("Keep names unique across periods", allNames.add(alarm.name))
                assertTrue(alarm.name.isNotBlank() && alarm.name.length <= 40)
            }
            assertTrue(exampleName in names)
            for (hour in hours) {
                assertTrue(AlarmConfig.defaultName(hour) in names)
                assertEquals(exampleName, AlarmConfig.defaultName(hour, names - exampleName))
                assertTrue(AlarmConfig.defaultName(hour, names) in names)
            }
        }
        assertEquals(32, allNames.size)
    }

    @Test
    fun newAndEditedAlarmsCanBeRestoredFromSavedInstanceState() {
        val initial = AlarmConfig.create(42, now = LocalTime.of(14, 37))
        val edited = initial.copy(
            name = "Early workout", hour = 5, minute = 42,
            weekdays = setOf(2, 4, 7), sound = AlarmSound.RICE_PAPER_DAWN,
            routine = listOf(PoseStep(YogaPose.TREE, 45), PoseStep(YogaPose.CHAIR, 10)),
            snoozeMinutes = 10, snoozeCount = 3, soundEnabled = false, stopButtonDisabled = true,
        )
        for (draft in listOf(initial, edited)) {
            val bytes = ByteArrayOutputStream().also { buffer ->
                ObjectOutputStream(buffer).use { it.writeObject(draft) }
            }.toByteArray()
            val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
            assertEquals(draft, restored)
        }
    }
}
