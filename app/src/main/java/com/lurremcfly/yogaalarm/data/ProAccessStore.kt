package com.lurremcfly.yogaalarm.data

import android.content.Context
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.model.ProPlan

class ProAccessStore(context: Context) {
    private val preferences = context.getSharedPreferences("pro_access", Context.MODE_PRIVATE)

    fun load(): ProPlan? {
        if (!BuildConfig.DEBUG) return null
        val storedPlan = preferences.getString(KEY_DEBUG_PLAN, null) ?: return null
        return ProPlan.entries.firstOrNull { it.name == storedPlan }
    }

    fun activateForTesting(plan: ProPlan) {
        check(BuildConfig.DEBUG) { "Test Pro access is only available in debug builds" }
        preferences.edit().putString(KEY_DEBUG_PLAN, plan.name).apply()
    }

    private companion object {
        const val KEY_DEBUG_PLAN = "debug_plan"
    }
}
