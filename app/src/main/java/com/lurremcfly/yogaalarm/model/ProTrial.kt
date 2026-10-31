package com.lurremcfly.yogaalarm.model

import java.time.LocalDate

enum class TrialEnrollment { INELIGIBLE, CONTROL, PREVIEW }
enum class FreeTransition { NOT_NEEDED, PENDING, APPLIED }

data class ProTrial(
    val enrollment: TrialEnrollment = TrialEnrollment.INELIGIBLE,
    val enrolledAt: Long = 0,
    val dates: Set<LocalDate> = emptySet(),
    val retired: Boolean = false,
    val pendingMilestone: Int? = null,
    val presented: Set<Int> = emptySet(),
    val transition: FreeTransition = FreeTransition.NOT_NEEDED,
    val awaitingPurchaseResolution: Boolean = false,
) {
    val remaining: Int get() = (5 - dates.size).coerceAtLeast(0)
    val active: Boolean get() = enrollment == TrialEnrollment.PREVIEW && !retired && remaining > 0
    val exhausted: Boolean get() = enrollment == TrialEnrollment.PREVIEW && !retired && remaining == 0

    fun needsFreeReview(alarm: AlarmConfig): Boolean =
        exhausted && !awaitingPurchaseResolution && !alarm.customization.isFree

    fun complete(date: LocalDate): ProTrial {
        if (!active || date in dates) return this
        val updated = dates + date
        return copy(
            dates = updated,
            pendingMilestone = updated.size.takeIf { it == 5 },
            transition = if (updated.size == 5) FreeTransition.PENDING else transition,
        )
    }

    fun retire() = copy(retired = true, pendingMilestone = null, transition = FreeTransition.NOT_NEEDED, awaitingPurchaseResolution = false)
    fun presented(milestone: Int) = if (pendingMilestone == milestone) {
        copy(pendingMilestone = null, presented = presented + milestone)
    } else this
}
