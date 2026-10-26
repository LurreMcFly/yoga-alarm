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
        YogaPose.entries.filterNot { it in listOf(YogaPose.CHAIR, YogaPose.FORWARD_FOLD, YogaPose.SQUAT, YogaPose.WIDE_LEG_FOLD) }.forEach { pose ->
            val hint = PoseScoring.framingHint(pose, bodyWithout(27, 28))
            assertFalse(hint.contains("ankle"))
            assertFalse(hint.contains("back"))
        }
    }

    @Test
    fun sidePosesDoNotAskForTheOccludedSide() {
        listOf(YogaPose.CHAIR, YogaPose.FORWARD_FOLD).forEach { pose ->
            assertEquals("Keep your side facing the camera", PoseScoring.framingHint(pose, bodyWithout(0, 12, 14, 16, 24, 26, 28)))
        }
    }

    @Test
    fun sidePosesAskForOneCompleteSideWhenFeetAreMissing() {
        listOf(YogaPose.CHAIR, YogaPose.FORWARD_FOLD).forEach { pose ->
            assertEquals("Turn sideways and include one full arm and leg, including your foot",
                PoseScoring.framingHint(pose, bodyWithout(27, 28)))
        }
    }

    @Test
    fun squatNeedsBothLegsButNotHeadElbowsOrBothWrists() {
        assertEquals("Keep facing the camera with both knees and feet in view",
            PoseScoring.framingHint(YogaPose.SQUAT, bodyWithout(0, 13, 14, 15)))
        assertEquals("Tilt the phone to include both feet",
            PoseScoring.framingHint(YogaPose.SQUAT, bodyWithout(28)))
        assertEquals("Tilt the phone to include both knees",
            PoseScoring.framingHint(YogaPose.SQUAT, bodyWithout(25)))
        assertEquals("Bring your hands to your chest in view",
            PoseScoring.framingHint(YogaPose.SQUAT, bodyWithout(15, 16)))
        assertEquals("Face the camera and bring both shoulders into view",
            PoseScoring.framingHint(YogaPose.SQUAT, bodyWithout(12)))
    }
}
