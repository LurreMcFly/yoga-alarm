package com.lurremcfly.yogaalarm.data

import android.content.Context
import android.net.Uri
import com.lurremcfly.yogaalarm.alarm.AlarmScheduler
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.AlarmCustomization
import com.lurremcfly.yogaalarm.model.AlarmSound
import com.lurremcfly.yogaalarm.model.PoseStep
import com.lurremcfly.yogaalarm.model.ProTrial
import com.lurremcfly.yogaalarm.model.YogaPose

class AlarmStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE)

    fun load(): List<AlarmConfig> = synchronized(AlarmStorageLock) { preferences.getString(KEY_ALARMS, null)
        ?.lineSequence()
        ?.mapNotNull(::decode)
        ?.toList()
        .orEmpty() }

    fun save(alarms: List<AlarmConfig>) = synchronized(AlarmStorageLock) {
        val previous = preferences.getString(KEY_ALARMS, null)
        val committed = preferences.edit()
            .putString(KEY_ALARMS, alarms.joinToString("\n", transform = ::encode))
            .commit()
        if (committed) WakeProgressStore(context).updateSchedule(alarms)
        else preferences.edit().putString(KEY_ALARMS, previous).commit()
        committed
    }

    fun update(change: (List<AlarmConfig>) -> List<AlarmConfig>): List<AlarmConfig> = synchronized(AlarmStorageLock) {
        val current = load()
        val updated = change(current)
        if (updated == current) return@synchronized current
        if (save(updated)) updated else current
    }

    fun reorder(ids: List<Long>): List<AlarmConfig> = update { current ->
        ids.distinct().mapNotNull { id -> current.firstOrNull { it.id == id } } + current.filter { it.id !in ids }
    }

    data class ScheduleResult(val alarm: AlarmConfig, val accepted: Boolean, val persisted: Boolean)

    // Serialize scheduling with edits/deletion so an old result cannot resurrect a record.
    fun schedule(expected: AlarmConfig, retry: Boolean = false, enabled: Boolean? = null): ScheduleResult? = synchronized(AlarmStorageLock) {
        val alarms = load()
        val current = alarms.firstOrNull { it.id == expected.id } ?: return@synchronized null
        if (current != expected || (retry && !current.scheduleNeedsRepair && !current.enabled)) return@synchronized null
        val requested = current.copy(enabled = enabled ?: if (retry) true else current.enabled)
        val occurrence = if (requested.enabled) runCatching { AlarmScheduler.register(context, requested) }.getOrNull() else null
        val accepted = occurrence != null
        val result = requested.copy(enabled = accepted, scheduleNeedsRepair = requested.enabled && !accepted)
        val persisted = result == current || save(alarms.map { if (it.id == current.id) result else it })
        when {
            !persisted && accepted -> AlarmScheduler.cancelRegular(context, current.id)
            persisted && !requested.enabled -> AlarmScheduler.cancel(context, current.id)
            persisted && occurrence == null -> AlarmScheduler.cancelRegular(context, current.id)
            persisted && occurrence != null -> WakeProgressStore(context).recordScheduledAlarm(result, occurrence)
        }
        ScheduleResult(if (persisted) result else current, accepted && persisted, persisted)
    }

    fun delete(id: Long): Boolean = synchronized(AlarmStorageLock) {
        if (!save(load().filterNot { it.id == id })) return@synchronized false
        AlarmScheduler.cancel(context, id)
        true
    }

    // Merge only fields edited by this draft; a receiver/expiry may have changed the rest.
    fun saveEdited(draft: AlarmConfig, base: AlarmConfig?, paid: Boolean, enableOnSave: Boolean = false): AlarmConfig? = synchronized(AlarmStorageLock) {
        val current = load().firstOrNull { it.id == draft.id }
        if (base != null && current == null) return@synchronized null
        val trialStore = ProTrialStore(context)
        val trial = if (trialStore.internalTestingEnabled() && !trialStore.internalTestTrialEnabled()) ProTrial() else trialStore.load()
        val hasPaidAccess = trialStore.hasPaidAccess(paid)
        if (!hasPaidAccess && !trial.active && !draft.customization.isFree &&
            current?.customization != draft.customization) return@synchronized null
        if (base != null && current != null && current.customization != base.customization && draft.customization != current.customization) {
            return@synchronized null // Require the visible draft to rebase before saving a conflicting customization.
        }
        val merged = if (base == null || current == null) draft else draft.copy(
            enabled = if (enableOnSave) true else if (draft.enabled != base.enabled) draft.enabled else current.enabled,
            name = if (draft.name != base.name) draft.name else current.name,
            hour = if (draft.hour != base.hour) draft.hour else current.hour,
            minute = if (draft.minute != base.minute) draft.minute else current.minute,
            weekdays = if (draft.weekdays != base.weekdays) draft.weekdays else current.weekdays,
            soundEnabled = if (draft.soundEnabled != base.soundEnabled) draft.soundEnabled else current.soundEnabled,
            vibrationEnabled = if (draft.vibrationEnabled != base.vibrationEnabled) draft.vibrationEnabled else current.vibrationEnabled,
            snoozeEnabled = if (draft.snoozeEnabled != base.snoozeEnabled) draft.snoozeEnabled else current.snoozeEnabled,
            snoozeCount = if (draft.snoozeCount != base.snoozeCount) draft.snoozeCount else current.snoozeCount,
            snoozeMinutes = if (draft.snoozeMinutes != base.snoozeMinutes) draft.snoozeMinutes else current.snoozeMinutes,
            stopButtonDisabled = if (draft.stopButtonDisabled != base.stopButtonDisabled) draft.stopButtonDisabled else current.stopButtonDisabled,
            scheduleNeedsRepair = if (enableOnSave || draft.enabled != base.enabled) false else current.scheduleNeedsRepair,
            trialOriginal = current.trialOriginal,
        )
        val named = merged.copy(name = merged.name.trim().take(40))
        val saved = when {
            !hasPaidAccess && trial.active && !named.customization.isFree -> named.copy(
                trialFallback = named.suggestedFreeFallback(), trialCustomization = named.customization)
            else -> named
        }
        // Persist an interrupted editor Save as repairable until AlarmManager accepts the new time.
        val staged = if (enableOnSave) saved.copy(enabled = false, scheduleNeedsRepair = true) else saved
        val result = update { list -> if (current == null) list + staged else list.map { if (it.id == staged.id) staged else it } }
        if (result.firstOrNull { it.id == staged.id } != staged) return@synchronized null
        if (enableOnSave) schedule(staged, retry = true)?.alarm else staged
    }

    internal fun stage(editor: android.content.SharedPreferences.Editor, alarms: List<AlarmConfig>) {
        editor.putString(KEY_ALARMS, alarms.joinToString("\n", transform = ::encode))
    }

    internal fun encode(alarm: AlarmConfig): String = listOf(
        alarm.id,
        Uri.encode(alarm.name),
        alarm.hour,
        alarm.minute,
        alarm.weekdays.sorted().joinToString("."),
        alarm.enabled,
        alarm.routine.joinToString(",") { "${it.pose.name}:${it.durationSeconds}" },
        alarm.soundEnabled,
        alarm.vibrationEnabled,
        alarm.snoozeEnabled,
        alarm.sound.name,
        alarm.snoozeMinutes,
        alarm.snoozeCount,
        alarm.stopButtonDisabled,
        encodeCustomization(alarm.trialFallback),
        encodeCustomization(alarm.trialCustomization),
        encodeCustomization(alarm.trialOriginal),
        alarm.scheduleNeedsRepair,
    ).joinToString("|")

    internal fun decode(encoded: String): AlarmConfig? = runCatching {
        val parts = encoded.split('|')
        AlarmConfig(
            id = parts[0].toLong(),
            name = Uri.decode(parts[1]),
            hour = parts[2].toInt(),
            minute = parts[3].toInt(),
            weekdays = parts[4].split('.').mapNotNull(String::toIntOrNull).toSet(),
            enabled = parts[5].toBooleanStrict(),
            routine = parts[6].split(',').map { step ->
                val (pose, duration) = step.split(':')
                PoseStep(YogaPose.valueOf(pose), duration.toInt())
            },
            soundEnabled = parts[7].toBooleanStrict(),
            vibrationEnabled = parts[8].toBooleanStrict(),
            snoozeEnabled = parts[9].toBooleanStrict(),
            sound = AlarmSound.fromStoredName(parts.getOrNull(10)),
            snoozeMinutes = parts.getOrNull(11)?.toIntOrNull() ?: 5,
            snoozeCount = parts.getOrNull(12)?.toIntOrNull() ?: 1,
            stopButtonDisabled = parts.getOrNull(13)?.toBooleanStrictOrNull() ?: false,
            trialFallback = decodeCustomization(parts.getOrNull(14)),
            trialCustomization = decodeCustomization(parts.getOrNull(15)),
            trialOriginal = decodeCustomization(parts.getOrNull(16)),
            scheduleNeedsRepair = parts.getOrNull(17)?.toBooleanStrictOrNull() ?: false,
        )
    }.getOrNull()

    private fun encodeCustomization(value: AlarmCustomization?): String = value?.let {
        it.routine.joinToString(",") { step -> "${step.pose.name}:${step.durationSeconds}" } + ";" + it.sound.name
    }.orEmpty()

    private fun decodeCustomization(value: String?): AlarmCustomization? = runCatching {
        if (value.isNullOrEmpty()) return null
        val parts = value.split(';')
        AlarmCustomization(parts[0].split(',').map {
            val step = it.split(':')
            PoseStep(YogaPose.valueOf(step[0]), step[1].toInt())
        }, AlarmSound.valueOf(parts[1]))
    }.getOrNull()

    private companion object {
        const val KEY_ALARMS = "alarms"
    }
}
