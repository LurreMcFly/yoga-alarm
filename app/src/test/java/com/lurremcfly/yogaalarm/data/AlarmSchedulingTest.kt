package com.lurremcfly.yogaalarm.data

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import com.lurremcfly.yogaalarm.alarm.BootReceiver
import com.lurremcfly.yogaalarm.model.AlarmConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulingTest {
    private lateinit var context: Context
    private lateinit var store: AlarmStore

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        listOf("yoga_alarm", "wake_progress", "alarm_occurrences").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        store = AlarmStore(context)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test fun failureSurvivesRestartAndRetryEnablesWithoutChangingConfiguration() {
        val alarm = AlarmConfig.create(1)
        store.save(listOf(alarm))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val failure = store.schedule(alarm)!!
        assertFalse(failure.accepted)
        assertTrue(failure.persisted)
        assertEquals(alarm.copy(enabled = false, scheduleNeedsRepair = true), AlarmStore(context).load().single())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val success = AlarmStore(context).schedule(failure.alarm, retry = true)!!
        assertTrue(success.accepted)
        assertEquals(alarm, store.load().single())
    }

    @Test fun explicitOffClearsRepairAndCannotBeRetriedOrRecovered() {
        val failed = AlarmConfig.create(1).copy(enabled = false, scheduleNeedsRepair = true)
        store.save(listOf(failed))
        val result = store.schedule(failed, enabled = false)!!
        assertFalse(result.alarm.scheduleNeedsRepair)
        assertNull(store.schedule(result.alarm, retry = true))
        assertFalse(store.load().single().enabled)
    }

    @Test fun editorSavePersistsRepairBeforeRegistrationAndGrantRetriesOnlyPendingAlarm() {
        val off = AlarmConfig.create(2).copy(enabled = false)
        store.save(listOf(off))
        val draft = AlarmConfig.create(1).copy(weekdays = emptySet())
        val failed = AlarmStore(failingAlarmCommit(2)).saveEdited(draft, null, paid = false, enableOnSave = true)!!
        assertEquals(draft.copy(enabled = false, scheduleNeedsRepair = true), failed)
        val manager = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertTrue(manager.scheduledAlarms.isEmpty())
        assertTrue(WakeProgressStore(context).load().upcomingAlarmDates.isEmpty())

        BootReceiver().onReceive(context, Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        assertEquals(listOf(off, draft), store.load())
        assertEquals(1, manager.scheduledAlarms.size)
        assertTrue(WakeProgressStore(context).load().upcomingAlarmDates.isNotEmpty())
    }

    @Test fun deletedOrEditedRecordsRejectOldSchedulingResult() {
        val alarm = AlarmConfig.create(1)
        store.save(listOf(alarm))
        val edited = alarm.copy(hour = (alarm.hour + 1) % 24)
        store.save(listOf(edited))
        assertNull(store.schedule(alarm))
        assertEquals(edited, store.load().single())
        store.save(emptyList())
        assertNull(store.schedule(edited))
        assertTrue(store.load().isEmpty())
    }

    @Test fun retryAndPermissionReconciliationPreserveSnoozedSnapshot() {
        val alarm = AlarmConfig.create(1)
        val occurrences = AlarmOccurrenceStore(context)
        val active = occurrences.begin(alarm, 1)
        assertTrue(occurrences.snooze(active.token, 0))
        val snapshot = occurrences.find(active.token)
        val failed = alarm.copy(enabled = false, scheduleNeedsRepair = true)
        store.save(listOf(failed))
        assertTrue(store.schedule(failed, retry = true)!!.accepted)
        assertEquals(snapshot, occurrences.find(active.token))
        assertTrue(store.schedule(store.load().single())!!.accepted)
        assertEquals(snapshot, occurrences.find(active.token))
    }

    @Test fun failedRepairCommitDoesNotClaimAlarmWasSwitchedOff() {
        val alarm = AlarmConfig.create(1)
        store.save(listOf(alarm))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val result = AlarmStore(failingAlarmCommit()).schedule(alarm)!!
        assertFalse(result.persisted)
        assertFalse(result.accepted)
        assertEquals(alarm, result.alarm)
        assertEquals(alarm, store.load().single())
    }

    @Test fun directToggleEnablesAndDisablesWithoutEditingConfiguration() {
        val off = AlarmConfig.create(1).copy(enabled = false)
        store.save(listOf(off))
        val on = store.schedule(off, enabled = true)!!
        assertTrue(on.accepted)
        assertEquals(off.copy(enabled = true), store.load().single())
        val stopped = store.schedule(on.alarm, enabled = false)!!
        assertTrue(stopped.persisted)
        assertEquals(off, store.load().single())
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }

    @Test fun failedOffAndDeleteCommitsPreserveRegistrationAndActiveOccurrence() {
        val alarm = AlarmConfig.create(1).copy(weekdays = emptySet())
        store.save(listOf(alarm))
        assertTrue(store.schedule(alarm)!!.accepted)
        val occurrences = AlarmOccurrenceStore(context)
        val active = occurrences.begin(alarm, 1)
        val registered = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.toList()
        val upcoming = WakeProgressStore(context).load().upcomingAlarmDates
        assertTrue(upcoming.isNotEmpty())
        val off = AlarmStore(failingAlarmCommit()).schedule(alarm, enabled = false)!!
        assertFalse(off.persisted)
        assertEquals(alarm, off.alarm)
        assertEquals(registered, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms)
        assertEquals(active, occurrences.find(active.token))
        assertEquals(upcoming, WakeProgressStore(context).load().upcomingAlarmDates)
        assertFalse(AlarmStore(failingAlarmCommit()).delete(alarm.id))
        assertEquals(alarm, store.load().single())
        assertEquals(registered, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms)
        assertEquals(active, occurrences.find(active.token))
        assertEquals(upcoming, WakeProgressStore(context).load().upcomingAlarmDates)
        assertFalse(store.schedule(off.alarm, enabled = false)!!.alarm.enabled)
        assertTrue(WakeProgressStore(context).load().upcomingAlarmDates.isEmpty())
    }

    @Test fun failedEnableCanRepeatTheOriginalRequestWithoutLeavingAStreakDate() {
        val off = AlarmConfig.create(1).copy(enabled = false, weekdays = emptySet())
        store.save(listOf(off))
        val failed = AlarmStore(failingAlarmCommit()).schedule(off, enabled = true)!!
        assertFalse(failed.persisted)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertTrue(WakeProgressStore(context).load().upcomingAlarmDates.isEmpty())
        assertTrue(store.schedule(failed.alarm, enabled = true)!!.accepted)
        assertTrue(WakeProgressStore(context).load().upcomingAlarmDates.isNotEmpty())
    }

    @Test fun olderRecordsDefaultToNoRepairRequest() {
        val alarm = AlarmConfig.create(1)
        assertEquals(alarm, store.decode(store.encode(alarm).substringBeforeLast('|')))
    }
    private fun failingAlarmCommit(at: Int = 1): Context {
        var commits = 0
        return object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = super.getSharedPreferences(name, mode)
                if (name != "yoga_alarm") return preferences
                return object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val delegate = preferences.edit()
                        return object : SharedPreferences.Editor by delegate {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                delegate.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean {
                                val committed = delegate.commit()
                                return ++commits != at && committed
                            }
                        }
                    }
                }
            }
        }
    }

}
