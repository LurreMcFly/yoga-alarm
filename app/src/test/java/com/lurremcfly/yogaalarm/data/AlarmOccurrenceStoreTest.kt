package com.lurremcfly.yogaalarm.data

import android.content.Context
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmSound
import com.lurremcfly.yogaalarm.model.OccurrenceState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmOccurrenceStoreTest {
    private lateinit var context: Context
    private lateinit var store: AlarmOccurrenceStore

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit().clear().commit()
        store = AlarmOccurrenceStore(context)
    }

    @Test fun moreThanSixteenSnoozedAlarmsRetainTheirLiveSnapshots() {
        val occurrences = (1L..20L).map { id ->
            store.begin(AlarmConfig.create(id), 1).also { assertTrue(store.snooze(it.token, 0)) }
        }
        val recreated = AlarmOccurrenceStore(context)
        occurrences.forEach { occurrence ->
            assertEquals(OccurrenceState.SNOOZED, recreated.find(occurrence.token)?.state)
            assertEquals(occurrence.alarm, recreated.find(occurrence.token)?.alarm)
        }
    }

    @Test fun replacingRingingServiceDoesNotCancelAnAlreadySnoozedOccurrence() {
        val ringing = store.begin(AlarmConfig.create(1), 1)
        val snoozed = store.begin(AlarmConfig.create(2), 1)
        assertTrue(store.snooze(snoozed.token, 0))
        store.stop(ringing.token, onlyIfRinging = true)
        store.stop(snoozed.token, onlyIfRinging = true)
        assertEquals(OccurrenceState.STOPPED, store.find(ringing.token)?.state)
        assertEquals(OccurrenceState.SNOOZED, store.find(snoozed.token)?.state)
    }

    @Test fun completionProgressJournalSurvivesRestartUntilAcknowledged() {
        val occurrence = store.begin(AlarmConfig.create(1), 1)
        val completedAt = ZonedDateTime.parse("2026-10-28T23:59:59+01:00[Europe/Stockholm]")
        val editor = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit()
        store.stageCompleted(editor, occurrence.token, completedAt)
        assertTrue(editor.commit())
        val recreated = AlarmOccurrenceStore(context)
        assertEquals(listOf(occurrence.token), recreated.pendingCompletions().map { it.token })
        assertEquals(completedAt, recreated.pendingCompletions().single().completedAt)
        assertTrue(recreated.markProgressRecorded(occurrence.token))
        assertTrue(AlarmOccurrenceStore(context).pendingCompletions().isEmpty())
        assertTrue(AlarmOccurrenceStore(context).find(occurrence.token)!!.terminal)
    }

    @Test fun snoozeKeepsSnapshotAfterFutureAlarmChangesAndStoreRecreation() {
        val alarm = AlarmConfig.create(1).copy(sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        AlarmStore(context).save(listOf(alarm))
        val occurrence = store.begin(alarm, 2)
        assertTrue(store.snooze(occurrence.token, 1))
        AlarmStore(context).save(listOf(alarm.copy(sound = AlarmSound.SUNBIRD_MORNING_CALL)))
        val restored = AlarmOccurrenceStore(context).ringing(occurrence.token)!!
        assertEquals(alarm, restored.alarm)
        assertEquals(1, restored.remainingSnoozes)
    }

    @Test fun completionRetainsOriginalTimestampAndNextDeliveryGetsFreshIdentity() {
        val alarm = AlarmConfig.create(1)
        AlarmStore(context).save(listOf(alarm))
        val first = store.begin(alarm, 1)
        val completedAt = ZonedDateTime.parse("2026-10-28T23:59:59+01:00[Europe/Stockholm]")
        val prefs = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)
        synchronized(AlarmStorageLock) {
            val editor = prefs.edit()
            store.stageCompleted(editor, first.token, completedAt)
            assertTrue(editor.commit())
            val replay = prefs.edit()
            store.stageCompleted(replay, first.token, completedAt.plusDays(1))
            assertTrue(replay.commit())
        }
        val restored = AlarmOccurrenceStore(context).find(first.token)!!
        assertTrue(restored.terminal)
        assertEquals(completedAt, restored.completedAt)
        assertNull(store.ringing(first.token))
        val next = store.begin(alarm, 1)
        assertNotEquals(first.token, next.token)
        assertEquals(OccurrenceState.RINGING, next.state)
    }

    @Test fun rebootInvalidatesPendingSnoozeWithoutReopeningTerminalRecords() {
        val alarm = AlarmConfig.create(1)
        val record = store.begin(alarm, 1)
        store.snooze(record.token, 0)
        store.clearSnoozes()
        assertEquals(OccurrenceState.STOPPED, store.find(record.token)?.state)
        assertNull(store.ringing(record.token))
    }
}
