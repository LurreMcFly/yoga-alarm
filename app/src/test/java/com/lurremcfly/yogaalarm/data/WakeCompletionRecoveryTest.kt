package com.lurremcfly.yogaalarm.data

import android.content.Context
import com.lurremcfly.yogaalarm.model.AlarmConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeCompletionRecoveryTest {
    private lateinit var context: Context
    private lateinit var progress: WakeProgressStore

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("wake_progress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit().clear().commit()
        progress = WakeProgressStore(context)
    }

    @Test fun olderCompletionReplayDoesNotRegressDateOrIncrementStreak() {
        val today = ZonedDateTime.now()
        val yesterday = today.minusDays(1)
        progress.recordCompletion(yesterday)
        val latest = progress.recordCompletion(today)

        val replayed = WakeProgressStore(context).recordCompletion(yesterday)
        val duplicate = WakeProgressStore(context).recordCompletion(today)

        assertEquals(today.toLocalDate(), replayed.lastCompletionDate)
        assertEquals(latest.streakDays, replayed.streakDays)
        assertEquals(latest.bestStreakDays, replayed.bestStreakDays)
        assertEquals(latest.streakDays, duplicate.streakDays)
        assertTrue(replayed.completedDates.containsAll(setOf(yesterday.toLocalDate(), today.toLocalDate())))
    }

    @Test fun crashBetweenProgressWriteAndJournalAcknowledgmentReplaysSafely() {
        val completedAt = ZonedDateTime.now().minusDays(1)
        val occurrences = AlarmOccurrenceStore(context)
        val occurrence = occurrences.begin(AlarmConfig.create(1), 1)
        val editor = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit()
        occurrences.stageCompleted(editor, occurrence.token, completedAt)
        assertTrue(editor.commit())
        progress.recordCompletion(completedAt)
        val latest = progress.recordCompletion(completedAt.plusDays(1))

        val recreated = AlarmOccurrenceStore(context)
        val pending = recreated.pendingCompletions().single()
        val recovered = WakeProgressStore(context).recordCompletion(pending.completedAt!!)
        assertTrue(recreated.markProgressRecorded(pending.token))

        assertEquals(latest.lastCompletionDate, recovered.lastCompletionDate)
        assertEquals(latest.streakDays, recovered.streakDays)
        assertTrue(AlarmOccurrenceStore(context).pendingCompletions().isEmpty())
        assertTrue(recreated.markProgressRecorded(pending.token))
        assertEquals(latest.streakDays, WakeProgressStore(context).load().streakDays)
    }
}
