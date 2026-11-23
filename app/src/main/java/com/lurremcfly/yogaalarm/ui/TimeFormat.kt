package com.lurremcfly.yogaalarm.ui

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lurremcfly.yogaalarm.data.TimeFormat
import android.icu.text.DateFormat as IcuDateFormat

@Composable
internal fun rememberTimeFormat(): TimeFormat {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var preference by remember(preferences) { mutableStateOf(TimeFormat.load(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TimeFormat.PREFERENCE_KEY) preference = TimeFormat.load(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return preference
}

@Composable
internal fun rememberIs24HourTime(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preference = rememberTimeFormat()
    var phoneUses24Hour by remember(context, configuration) { mutableStateOf(DateFormat.is24HourFormat(context)) }
    DisposableEffect(context, lifecycleOwner, configuration) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) phoneUses24Hour = DateFormat.is24HourFormat(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return preference.is24Hour(phoneUses24Hour)
}

@Composable
internal fun rememberClockFormatter(is24Hour: Boolean = rememberIs24HourTime()): IcuDateFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, is24Hour) { TimeFormat.formatter(locale, is24Hour) }
}
