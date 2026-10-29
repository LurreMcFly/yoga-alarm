package com.lurremcfly.yogaalarm.billing

import android.content.SharedPreferences

/** Stores only authenticated opaque evidence. The verifier, not preferences, grants access. */
internal class PaidEntitlementCache(
    private val preferences: SharedPreferences,
    private val verifier: RecoverablePurchaseVerifier?,
) {
    fun restore(nowEpochMillis: Long): VerifiedPaidAccess? {
        return runCatching {
            val evidence = preferences.getString("verified_paid_evidence", null) ?: return null
            verifier?.restoreCached(evidence, nowEpochMillis)
        }.getOrNull()
            ?.takeIf { it.isValidAt(nowEpochMillis) }
    }

    fun save(access: VerifiedPaidAccess, nowEpochMillis: Long): Boolean {
        if (!access.isValidAt(nowEpochMillis)) return false
        val restored = runCatching {
            verifier?.restoreCached(access.cacheEvidence, nowEpochMillis)
        }.getOrNull() ?: return false
        if (restored != access) return false
        return preferences.edit().putString("verified_paid_evidence", access.cacheEvidence).commit()
    }

    fun clear(): Boolean = preferences.edit().remove("verified_paid_evidence").commit()
}
