package com.lurremcfly.yogaalarm.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

internal class AnalyticsSink(context: Context) {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext).also {
        it.setAnalyticsCollectionEnabled(true)
    }

    // Success means accepted by the SDK, not confirmed delivery or authoritative revenue.
    fun record(name: String, params: Bundle): Boolean = runCatching {
        analytics.logEvent(name, params)
        true
    }.getOrDefault(false)
}
