package com.lurremcfly.yogaalarm.model

import java.time.ZonedDateTime

enum class OccurrenceState { RINGING, SNOOZED, COMPLETED, STOPPED }

data class AlarmOccurrence(
    val token: String,
    val alarm: AlarmConfig,
    val remainingSnoozes: Int,
    val state: OccurrenceState = OccurrenceState.RINGING,
    val completedAt: ZonedDateTime? = null,
    val progressRecorded: Boolean = false,
) {
    val terminal: Boolean get() = state == OccurrenceState.COMPLETED || state == OccurrenceState.STOPPED
}
