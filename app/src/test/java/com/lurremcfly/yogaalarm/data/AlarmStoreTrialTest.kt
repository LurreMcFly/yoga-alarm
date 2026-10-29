package com.lurremcfly.yogaalarm.data

import android.content.Context
import com.lurremcfly.yogaalarm.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmStoreTrialTest {
    private lateinit var context: Context
    private lateinit var store: AlarmStore

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        listOf("yoga_alarm", "onboarding", "wake_progress").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        store = AlarmStore(context)
    }

    @Test fun staleEditorSchedulingChangesPreserveReceiverDisableAndLatestUneditedFields() {
        val base = AlarmConfig.create(1)
        store.save(listOf(base.copy(enabled = false, weekdays = setOf(7), vibrationEnabled = false)))
        val saved = store.saveEdited(base.copy(name = "Changed name", hour = 6), base, false)!!
        assertFalse(saved.enabled)
        assertFalse(saved.vibrationEnabled)
        assertEquals(setOf(7), saved.weekdays)
        assertEquals("Changed name", saved.name)
        assertEquals(6, saved.hour)
    }

    @Test fun stalePaidDraftCannotOverwriteConvertedAlarmOrResurrectDeletedAlarm() {
        val draft = AlarmConfig.create(1).copy(sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        val converted = draft.copy(sound = AlarmSound.SUNBIRD_MORNING_CALL, trialOriginal = draft.customization)
        store.save(listOf(converted))
        assertNull(store.saveEdited(draft.copy(name = "Stale"), draft, false))
        assertEquals(converted, store.load().single())
        store.update { emptyList() }
        assertNull(store.saveEdited(draft, draft, true))
        assertTrue(store.load().isEmpty())
    }

    @Test fun explicitEditorSaveCanReenableAlarmDisabledSinceDraftOpened() {
        val base = AlarmConfig.create(1)
        store.save(listOf(base.copy(enabled = false)))
        val saved = store.saveEdited(base.copy(name = "Ready for tomorrow"), base, paid = false, enableOnSave = true)!!
        assertTrue(saved.enabled)
        assertTrue(store.load().single().enabled)
        assertEquals("Ready for tomorrow", saved.name)
    }

    @Test fun activePreviewRequiresConfirmedFallbackBoundToCurrentCustomization() {
        ProTrialStore(context).enroll(true, true)
        val draft = AlarmConfig.create(1).copy(sound = AlarmSound.LOTUS_SUNRISE_LOOP)
        assertNull(store.saveEdited(draft, null, false))
        val mismatched = draft.copy(trialFallback = draft.suggestedFreeFallback(), trialCustomization = AlarmConfig.create(1).customization)
        assertNull(store.saveEdited(mismatched, null, false))
        val confirmed = mismatched.copy(trialCustomization = draft.customization)
        assertEquals(confirmed, store.saveEdited(confirmed, null, false))
        assertEquals(confirmed, AlarmStore(context).load().single())
    }

    @Test fun serializationPreservesFallbackOriginalAndScheduleWithoutNormalizingOrdinaryLoad() {
        val paid = AlarmConfig.create(1).copy(routine = List(10) { PoseStep(YogaPose.GODDESS, 45) }, sound = AlarmSound.RICE_PAPER_DAWN)
        val alarm = paid.copy(enabled = false, trialOriginal = paid.customization, trialFallback = paid.suggestedFreeFallback(), trialCustomization = paid.customization)
        store.save(listOf(alarm))
        assertEquals(alarm, AlarmStore(context).load().single())
        assertEquals(alarm, store.decode(store.encode(alarm)))
    }
}
