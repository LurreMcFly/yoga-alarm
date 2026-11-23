package com.lurremcfly.yogaalarm.ui

import android.app.Activity
import android.content.Context
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraPermissionTest {
    @Test
    fun previousRequestsAndExpiredGrantsCanPromptAgain() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("alarm_readiness", Context.MODE_PRIVATE)
            .edit().putBoolean("camera_requested", true).commit()

        assertFalse(openSettingsForDeniedCamera(activity))
        recordCameraPermissionResult(activity, granted = false)
        assertTrue(openSettingsForDeniedCamera(activity))
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity).nextStartedActivity.action)

        recordCameraPermissionResult(activity, granted = true)
        assertFalse(openSettingsForDeniedCamera(activity))
    }
}
