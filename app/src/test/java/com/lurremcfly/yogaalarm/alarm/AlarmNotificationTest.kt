package com.lurremcfly.yogaalarm.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.model.AlarmConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@Suppress("DEPRECATION") // Inspect the actual custom RemoteViews supplied to Android.
class AlarmNotificationTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var controller: ServiceController<AlarmForegroundService>
    private lateinit var service: AlarmForegroundService

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(NotificationManager::class.java)
        controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    @Test
    fun dismissedNotificationRestoresControlsWithoutReopeningAlarmOrChangingSnoozes() {
        val notification = ring(alarm(), remainingSnoozes = 1)
        assertNotNull(notification.fullScreenIntent)
        manager.cancel(AlarmScheduler.NOTIFICATION_ID)

        notification.deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()

        val restored = shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID)
        assertNotNull(restored)
        assertNull(restored.fullScreenIntent)
        assertTrue(NotificationCompat.getOngoing(restored))
        assertTrue(NotificationCompat.getOnlyAlertOnce(restored))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, restored.groupAlertBehavior)
        assertEquals(1L to 1, AlarmForegroundService.ringingAlarm.value)
        assertNull(service.finishedAlarmId.value)
        assertNotNull(restored.contentView)
        assertNotNull(restored.bigContentView)
        assertNotNull(restored.headsUpContentView)
    }

    @Test
    fun dismissalAfterAlarmFinishesDoesNotRestoreNotification() {
        val notification = ring(alarm())
        service.finishRoutine(1L)
        manager.cancel(AlarmScheduler.NOTIFICATION_ID)

        notification.deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
        assertNull(AlarmForegroundService.ringingAlarm.value)
    }

    @Test
    fun oldDismissalDoesNotChangeNewAlarmNotification() {
        val old = ring(alarm())
        ring(alarm(id = 2L))

        old.deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()

        val current = shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID)
        assertNotNull(current.fullScreenIntent)
        assertFalse(NotificationCompat.getOnlyAlertOnce(current))
        assertEquals(2L to 1, AlarmForegroundService.ringingAlarm.value)
    }

    @Test
    fun notificationButtonsFitNarrowAndWideContentWithEqualHeights() {
        val notification = ring(alarm().copy(snoozeMinutes = 20))
        try {
            for (fontScale in listOf(1f, 1.3f, 2f)) {
                RuntimeEnvironment.setFontScale(fontScale)
                for (width in listOf(240, 320, 480)) {
                    for (content in listOf(notification.contentView, notification.bigContentView, notification.headsUpContentView)) {
                        val view = inflate(content, width)
                        val buttons = listOf(R.id.notification_start, R.id.notification_stop, R.id.notification_snooze)
                            .map { view.findViewById<Button>(it) }
                        assertTrue(buttons.all { it.height == buttons.first().height })
                        for (button in buttons) {
                            assertTrue("Button must fit within its allocated width", button.width <= (button.parent as View).width)
                            for (line in 0 until button.layout.lineCount) {
                                assertEquals("${button.text} should fit at width $width, font scale $fontScale", 0, button.layout.getEllipsisCount(line))
                            }
                        }
                    }
                }
            }
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    fun secondaryButtonsShareWidthAndSingleSnoozeStaysCenteredAndNarrow() {
        val both = inflate(ring(alarm()).bigContentView, 320)
        assertEquals(
            both.findViewById<Button>(R.id.notification_stop).width,
            both.findViewById<Button>(R.id.notification_snooze).width,
        )
        val single = inflate(ring(alarm().copy(stopButtonDisabled = true)).bigContentView, 320)
        val row = single.findViewById<View>(R.id.notification_secondary_actions)
        val container = single.findViewById<View>(R.id.notification_snooze_container)
        val snooze = single.findViewById<Button>(R.id.notification_snooze)
        assertEquals(View.GONE, single.findViewById<View>(R.id.notification_stop_container).visibility)
        assertEquals(row.width / 2f, container.left + snooze.left + snooze.width / 2f, 1f)
        assertTrue(snooze.width < row.width)
        assertEquals(both.findViewById<Button>(R.id.notification_snooze).height, snooze.height)
    }

    @Test
    fun snoozeIsHiddenWhenDisabledOrExhausted() {
        for (notification in listOf(
            ring(alarm().copy(snoozeEnabled = false)),
            ring(alarm(id = 2L), remainingSnoozes = 0),
        )) {
            for (content in listOf(notification.contentView, notification.bigContentView, notification.headsUpContentView)) {
                val view = inflate(content, 320)
                assertEquals(View.GONE, view.findViewById<View>(R.id.notification_snooze_container).visibility)
            }
        }
    }

    private fun alarm(id: Long = 1L) = AlarmConfig.create(id).copy(
        name = "Morning movement", soundEnabled = false, vibrationEnabled = false,
    )

    private fun ring(alarm: AlarmConfig, remainingSnoozes: Int = 1): Notification {
        // A new occurrence is required when changing settings for the same alarm.
        AlarmForegroundService.ringingAlarm.value?.first?.let(service::finishRoutine)
        AlarmStore(context).save(listOf(alarm))
        service.onStartCommand(
            Intent(context, AlarmForegroundService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                .putExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, remainingSnoozes),
            0,
            1,
        )
        return shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID)
    }

    private fun inflate(content: RemoteViews, width: Int): View {
        val view = content.apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }
}
