package com.lurremcfly.yogaalarm.data

import android.content.Context
import android.provider.Settings
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimeFormatTest {
    @Test
    fun missingAndUnknownPreferencesFollowPhone() {
        assertEquals(TimeFormat.FOLLOW_PHONE, TimeFormat.fromStored(null))
        assertEquals(TimeFormat.FOLLOW_PHONE, TimeFormat.fromStored("unknown"))
        TimeFormat.entries.forEach { assertEquals(it, TimeFormat.fromStored(it.storedValue)) }
        assertFalse(TimeFormat.FOLLOW_PHONE.is24Hour(false))
        assertTrue(TimeFormat.FOLLOW_PHONE.is24Hour(true))
        assertTrue(TimeFormat.HOUR_24.is24Hour(false))
        assertFalse(TimeFormat.HOUR_12.is24Hour(true))
    }

    @Test
    fun clocksCoverMidnightNoonAndLastMinute() {
        val clock24 = TimeFormat.formatter(Locale.US, true)
        val clock12 = TimeFormat.formatter(Locale.US, false)
        assertEquals("00:00", clock24.formatClock(0, 0))
        assertEquals("12:00", clock24.formatClock(12, 0))
        assertEquals("23:59", clock24.formatClock(23, 59))
        // CLDR versions differ on the space separating the US day period.
        assertEquals("12:00AM", clock12.formatClock(0, 0).filterNot(Char::isWhitespace))
        assertEquals("12:00PM", clock12.formatClock(12, 0).filterNot(Char::isWhitespace))
        assertEquals("11:59PM", clock12.formatClock(23, 59).filterNot(Char::isWhitespace))
    }

    @Test
    fun clocksUseLocaleDigitsAndDayPeriodPlacement() {
        assertTrue(TimeFormat.formatter(Locale.TAIWAN, false).formatClock(19, 45).endsWith("7:45"))
        assertEquals("١٩:٤٥", TimeFormat.formatter(Locale.forLanguageTag("ar-EG"), true).formatClock(19, 45))
        assertEquals("19.45", TimeFormat.formatter(Locale.forLanguageTag("fi-FI"), true).formatClock(19, 45))
    }

    @Test
    fun editorRoundTripsAllCanonicalHoursAndAmPm() {
        (0..23).forEach { hour ->
            assertEquals(hour, TimeFormat.canonicalHour(TimeFormat.displayHour(hour), hour >= 12))
        }
        assertEquals(0, TimeFormat.canonicalHour(12, false))
        assertEquals(12, TimeFormat.canonicalHour(12, true))
        assertEquals(23, TimeFormat.canonicalHour(11, true))
    }

    @Test
    fun formatterReloadsPreferenceAndCurrentPhoneFormat() {
        val context = RuntimeEnvironment.getApplication() as Context
        val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val locale = context.resources.configuration.locales[0]
        val twelve = TimeFormat.formatter(locale, false).formatClock(23, 59)
        val twentyFour = TimeFormat.formatter(locale, true).formatClock(23, 59)
        assertFalse(twelve == twentyFour)
        preferences.edit().clear().commit()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertEquals(twelve, TimeFormat.formatter(context).formatClock(23, 59))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertEquals(twentyFour, TimeFormat.formatter(context).formatClock(23, 59))
        preferences.edit().putString(TimeFormat.PREFERENCE_KEY, TimeFormat.HOUR_12.storedValue).commit()
        assertEquals(twelve, TimeFormat.formatter(context).formatClock(23, 59))
        preferences.edit().putString(TimeFormat.PREFERENCE_KEY, TimeFormat.HOUR_24.storedValue).commit()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertEquals(twentyFour, TimeFormat.formatter(context).formatClock(23, 59))
    }
}
