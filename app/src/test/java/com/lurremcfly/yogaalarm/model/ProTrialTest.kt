package com.lurremcfly.yogaalarm.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProTrialTest {
    @Test fun fiveDistinctSuccessDatesCountWithoutConsecutiveDayRequirement() {
        var trial = ProTrial(enrollment = TrialEnrollment.PREVIEW)
        val first = LocalDate.of(2026, 10, 1)
        for (index in 0..4) {
            val date = first.plusDays(index * 7L)
            trial = trial.complete(date)
            assertEquals(index + 1, trial.dates.size)
            assertEquals(trial, trial.complete(date))
        }
        assertEquals(0, trial.remaining)
        assertFalse(trial.active)
        assertTrue(trial.exhausted)
        assertEquals(FreeTransition.PENDING, trial.transition)
        assertEquals(trial, trial.complete(first.plusDays(100)))
    }

    @Test fun onlyExpiryQueuesAnOfferAndPresentationIsIdempotent() {
        var trial = ProTrial(enrollment = TrialEnrollment.PREVIEW)
        val first = LocalDate.of(2026, 10, 1)
        repeat(4) {
            trial = trial.complete(first.plusDays(it.toLong()))
            assertNull(trial.pendingMilestone)
        }
        trial = trial.complete(first.plusDays(4))
        assertEquals(5, trial.pendingMilestone)
        trial = trial.presented(5)
        assertNull(trial.pendingMilestone)
        assertEquals(setOf(5), trial.presented)
        assertEquals(trial, trial.presented(5))
    }

    @Test fun controlIneligibleAndRetiredTrialsNeverConsumeDates() {
        val date = LocalDate.of(2026, 10, 1)
        for (trial in listOf(ProTrial(), ProTrial(enrollment = TrialEnrollment.CONTROL), ProTrial(enrollment = TrialEnrollment.PREVIEW).retire())) {
            assertFalse(trial.active)
            assertEquals(trial, trial.complete(date))
        }
        val retired = ProTrial(enrollment = TrialEnrollment.PREVIEW, pendingMilestone = 2).retire()
        assertNull(retired.pendingMilestone)
        assertEquals(FreeTransition.NOT_NEEDED, retired.transition)
    }

    @Test fun fallbackPadsTruncatesReplacesPaidPosesAndAlwaysUsesTwentySecondHolds() {
        for (size in listOf(1, 2, 3, 10)) {
            for (duration in listOf(5, 10, 15, 20, 30, 45, 60)) {
                val alarm = AlarmConfig.create(1).copy(
                    routine = List(size) { PoseStep(if (it == 0) YogaPose.SQUAT else YogaPose.CHAIR, duration) },
                    sound = AlarmSound.LOTUS_SUNRISE_LOOP,
                )
                val fallback = alarm.suggestedFreeFallback()
                assertTrue(fallback.isFree)
                assertEquals(listOf(YogaPose.SQUAT, YogaPose.WARRIOR_TWO, YogaPose.TREE), fallback.routine.map { it.pose })
                assertEquals(listOf(20, 20, 20), fallback.routine.map { it.durationSeconds })
                assertEquals(AlarmSound.SUNBIRD_MORNING_CALL, fallback.sound)
            }
        }
        val duplicate = AlarmConfig.create(1).copy(routine = List(3) { PoseStep(YogaPose.SQUAT) }, sound = AlarmSound.MORNING_TEMPLE_CALL)
        assertEquals(duplicate.customization, duplicate.suggestedFreeFallback())
    }
}
