package com.lurremcfly.yogaalarm.data

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.text.format.DateFormat
import android.icu.text.DateFormat as IcuDateFormat
import java.util.Locale

enum class TimeFormat(val storedValue: String, val label: String) {
    FOLLOW_PHONE("follow_phone", "Follow phone"),
    HOUR_24("24_hour", "24-hour"),
    HOUR_12("12_hour", "12-hour (AM/PM)");

    fun is24Hour(phoneUses24Hour: Boolean): Boolean = when (this) {
        FOLLOW_PHONE -> phoneUses24Hour
        HOUR_24 -> true
        HOUR_12 -> false
    }

    companion object {
        const val PREFERENCE_KEY = "time_format"

        fun fromStored(value: String?): TimeFormat = entries.firstOrNull { it.storedValue == value } ?: FOLLOW_PHONE

        fun load(context: Context): TimeFormat = fromStored(
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString(PREFERENCE_KEY, null),
        )

        fun formatter(context: Context): IcuDateFormat = formatter(
            context.resources.configuration.locales[0],
            load(context).is24Hour(DateFormat.is24HourFormat(context)),
        )

        fun formatter(locale: Locale, is24Hour: Boolean): IcuDateFormat =
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)

        fun displayHour(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }

        fun canonicalHour(displayHour: Int, isPm: Boolean): Int = displayHour % 12 + if (isPm) 12 else 0
    }
}

internal fun IcuDateFormat.formatClock(hour: Int, minute: Int): String {
    // A saved wall-clock time can fall in today's local DST gap. Format its fields in UTC.
    return format(Calendar.getInstance(TimeZone.GMT_ZONE).apply {
        clear()
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    })
}
