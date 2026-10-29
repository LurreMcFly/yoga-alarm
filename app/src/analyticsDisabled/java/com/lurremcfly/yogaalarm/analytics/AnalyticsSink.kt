package com.lurremcfly.yogaalarm.analytics

import android.content.Context
import android.os.Bundle

internal class AnalyticsSink(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun record(@Suppress("UNUSED_PARAMETER") name: String, @Suppress("UNUSED_PARAMETER") params: Bundle): Boolean = false
}
