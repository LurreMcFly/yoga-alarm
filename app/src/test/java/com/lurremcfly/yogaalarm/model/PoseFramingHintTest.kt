package com.lurremcfly.yogaalarm.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PoseFramingHintTest {
    private fun bodyWithout(vararg missing: Int) = BodyLandmarks(List(33) { index ->
        BodyPoint(0.5f, 0.5f, 0f, if (index in missing) 0f else 1f, 1f)
    })

    @Test
    fun treeDoesNotAskForHandsOrAnkles() {
        assertEquals("Stay where you can comfortably see the screen", PoseScoring.framingHint(YogaPose.TREE, bodyWithout(15, 16, 27, 28)))
    }

    @Test
    fun missingHandsProduceAnActionableHintForWarrior() {
        assertEquals("Bring your hands into view", PoseScoring.framingHint(YogaPose.WARRIOR_TWO, bodyWithout(15)))
    }

    @Test
    fun optionalAnklesNeverPromptSteppingBack() {
        YogaPose.entries.forEach { pose ->
            val hint = PoseScoring.framingHint(pose, bodyWithout(27, 28))
            assertFalse(hint.contains("ankle"))
            assertFalse(hint.contains("back"))
        }
    }
}
