package com.lurremcfly.yogaalarm.billing

import android.content.SharedPreferences

/** A client ownership observation, not authenticated store expiry or a server-signed grant. */
internal data class LastKnownPaidAccess(
    val entitlement: ProEntitlement,
    val lastOnlineCheckEpochMillis: Long,
) {
    val validUntilEpochMillis: Long?
        get() = if (entitlement == ProEntitlement.SUBSCRIPTION) {
            lastOnlineCheckEpochMillis + SUBSCRIPTION_GRACE_MILLIS
        } else null

    fun isValidAt(nowEpochMillis: Long): Boolean = lastOnlineCheckEpochMillis >= 0 &&
        lastOnlineCheckEpochMillis <= Long.MAX_VALUE - SUBSCRIPTION_GRACE_MILLIS &&
        when (entitlement) {
            ProEntitlement.LIFETIME -> true
            ProEntitlement.SUBSCRIPTION -> nowEpochMillis >= lastOnlineCheckEpochMillis &&
                nowEpochMillis < requireNotNull(validUntilEpochMillis)
        }

    companion object {
        const val SUBSCRIPTION_GRACE_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}

internal class LastKnownPaidAccessCache(private val preferences: SharedPreferences) {
    fun restore(): LastKnownPaidAccess? = runCatching {
        val plan = preferences.getString("last_known_paid_plan", null) ?: return null
        val checkedAt = preferences.getLong("last_known_paid_online_check", -1L)
        if (checkedAt < 0 || checkedAt > Long.MAX_VALUE - LastKnownPaidAccess.SUBSCRIPTION_GRACE_MILLIS) return null
        LastKnownPaidAccess(ProEntitlement.valueOf(plan), checkedAt)
    }.getOrNull()

    fun save(access: LastKnownPaidAccess): Boolean {
        if (access.lastOnlineCheckEpochMillis < 0 ||
            access.lastOnlineCheckEpochMillis > Long.MAX_VALUE - LastKnownPaidAccess.SUBSCRIPTION_GRACE_MILLIS
        ) return false
        return preferences.edit()
            .putString("last_known_paid_plan", access.entitlement.name)
            .putLong("last_known_paid_online_check", access.lastOnlineCheckEpochMillis)
            .commit()
    }

    fun clear(): Boolean = preferences.edit()
        .remove("last_known_paid_plan")
        .remove("last_known_paid_online_check")
        .commit()
}
