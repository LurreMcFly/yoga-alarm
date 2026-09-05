package com.lurremcfly.yogaalarm.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDraftTest {
    @Test
    fun editedAlarmCanBeRestoredFromSavedInstanceState() {
        val draft = AlarmConfig.create(42).copy(
            name = "Early workout", hour = 5, minute = 42,
            weekdays = setOf(2, 4, 7), sound = AlarmSound.RICE_PAPER_DAWN,
            routine = listOf(PoseStep(YogaPose.TREE, 45), PoseStep(YogaPose.CHAIR, 10)),
            snoozeMinutes = 10, snoozeCount = 3, soundEnabled = false,
        )
        val bytes = ByteArrayOutputStream().also { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(draft) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertEquals(draft, restored)
    }
}
