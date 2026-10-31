package com.lurremcfly.yogaalarm.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.lurremcfly.yogaalarm.MainActivity
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.data.AlarmStore
import com.lurremcfly.yogaalarm.data.AlarmOccurrenceStore
import com.lurremcfly.yogaalarm.model.AlarmConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.util.Date

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
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    @Test
    fun snoozedOccurrenceCannotBeReattachedBeforeItsDelivery() {
        ring(alarm())
        val token = AlarmForegroundService.ringingOccurrenceToken.value!!
        AlarmOccurrenceStore(context).snooze(token, 0)
        service.finishRoutine(1L, token, persistStop = false)
        controller.destroy()
        controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        service = controller.get()
        service.onStartCommand(Intent(context, AlarmForegroundService::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, token), 0, 1)
        assertNull(AlarmForegroundService.ringingAlarm.value)
        assertNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
    }

    @Test
    fun staleStopAndCompletedReattachmentCannotStopNextOccurrenceOfSameAlarm() {
        ring(alarm())
        val oldToken = AlarmForegroundService.ringingOccurrenceToken.value!!
        service.finishRoutine(1L, oldToken, persistStop = false)
        val completedAt = java.time.ZonedDateTime.now()
        val editor = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit()
        AlarmOccurrenceStore(context).stageCompleted(editor, oldToken, completedAt)
        assertTrue(editor.commit())
        val current = ring(alarm())
        val nextToken = AlarmForegroundService.ringingOccurrenceToken.value

        service.onStartCommand(Intent(context, AlarmForegroundService::class.java)
            .setAction(AlarmForegroundService.ACTION_STOP_ALARM)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, oldToken), 0, 2)
        service.onStartCommand(Intent(context, AlarmForegroundService::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, oldToken), 0, 3)

        assertEquals(nextToken, AlarmForegroundService.ringingOccurrenceToken.value)
        assertSame(current, shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
    }

    @Test
    fun completedOccurrenceCannotRestartPlaybackAfterServiceRecreation() {
        ring(alarm())
        val token = AlarmForegroundService.ringingOccurrenceToken.value!!
        service.finishRoutine(1L, token, persistStop = false)
        val editor = context.getSharedPreferences("yoga_alarm", Context.MODE_PRIVATE).edit()
        AlarmOccurrenceStore(context).stageCompleted(editor, token, java.time.ZonedDateTime.now())
        assertTrue(editor.commit())
        controller.destroy()
        controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        service = controller.get()

        service.onStartCommand(Intent(context, AlarmForegroundService::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, token), 0, 1)

        assertNull(AlarmForegroundService.ringingAlarm.value)
        assertNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
    }

    @Test
    fun startRequestsCameraFromEveryNotificationLayoutWithoutChangingOtherOpenActions() {
        val notification = ring(alarm(), remainingSnoozes = 2)
        val application = shadowOf(RuntimeEnvironment.getApplication())

        for (content in listOf(notification.contentView, notification.bigContentView, notification.headsUpContentView)) {
            assertTrue(inflate(content, 320).findViewById<View>(R.id.notification_start).performClick())
            val intent = application.nextStartedActivity
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(AlarmScheduler.ACTION_START_CAMERA, intent.action)
            assertEquals(1L, intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L))
            assertEquals(2, intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, -1))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        }

        for (open in listOf(notification.contentIntent, notification.fullScreenIntent)) {
            assertNull(shadowOf(open).savedIntent.action)
            assertEquals(1L, shadowOf(open).savedIntent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L))
        }
        assertEquals(1L to 2, AlarmForegroundService.ringingAlarm.value)
        assertNull(service.finishedAlarmId.value)
    }

    @Test
    fun startKeepsCurrentSnoozeCountAfterNotificationIsRecreated() {
        ring(alarm(), remainingSnoozes = 2)
        ring(alarm(), remainingSnoozes = 1)
        service.dismissNotificationPopup(1L)
        val notification = shadowOf(service).lastForegroundNotification

        assertTrue(inflate(notification.bigContentView, 320).findViewById<View>(R.id.notification_start).performClick())
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(AlarmScheduler.ACTION_START_CAMERA, intent.action)
        assertEquals(1, intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, -1))
        assertNull(shadowOf(notification.contentIntent).savedIntent.action)
        assertNull(notification.fullScreenIntent)
    }

    @Test
    fun openingAlarmDismissesPopupWhileKeepingPlaybackAndControlsActive() {
        val notification = ring(alarm(), remainingSnoozes = 2)

        service.dismissNotificationPopup(1L)

        assertTrue(shadowOf(service).lastForegroundNotificationId != AlarmScheduler.NOTIFICATION_ID)
        assertNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
        val updated = shadowOf(service).lastForegroundNotification
        assertNotNull(updated)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(updated.channelId).importance)
        assertNull(updated.headsUpContentView)
        assertNull(updated.fullScreenIntent)
        assertTrue(NotificationCompat.getOngoing(updated))
        assertTrue(NotificationCompat.getOnlyAlertOnce(updated))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, updated.groupAlertBehavior)
        assertEquals(notification.contentIntent, updated.contentIntent)
        assertEquals(1L to 2, AlarmForegroundService.ringingAlarm.value)
        assertNull(service.finishedAlarmId.value)
        for (content in listOf(updated.contentView, updated.bigContentView)) {
            val view = inflate(content, 320)
            for (id in listOf(R.id.notification_start, R.id.notification_stop, R.id.notification_snooze)) {
                assertEquals(View.VISIBLE, view.findViewById<View>(id).visibility)
            }
        }
    }

    @Test
    fun openingStaleAlarmDoesNotDismissNewAlarmPopup() {
        ring(alarm())
        service.dismissNotificationPopup(1L)
        val notification = ring(alarm(id = 2L))

        service.dismissNotificationPopup(1L)

        val current = shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID)
        assertSame(notification, current)
        assertEquals(AlarmScheduler.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        assertNotNull(current.fullScreenIntent)
        assertFalse(NotificationCompat.getOnlyAlertOnce(current))
        assertEquals(2L to 1, AlarmForegroundService.ringingAlarm.value)
    }

    @Test
    fun openingFinishedAlarmDoesNotRestoreNotification() {
        ring(alarm())
        service.finishRoutine(1L)

        service.dismissNotificationPopup(1L)

        assertNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
        assertNull(AlarmForegroundService.ringingAlarm.value)
    }

    @Test
    fun dismissedNotificationRestoresControlsWithoutReopeningAlarmOrChangingSnoozes() {
        val notification = ring(alarm(), remainingSnoozes = 1)
        assertNotNull(notification.fullScreenIntent)
        manager.cancel(AlarmScheduler.NOTIFICATION_ID)

        notification.deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()

        val restored = shadowOf(manager).getNotification(shadowOf(service).lastForegroundNotificationId)
        assertNotNull(restored)
        assertNull(restored.fullScreenIntent)
        assertTrue(NotificationCompat.getOngoing(restored))
        assertTrue(NotificationCompat.getOnlyAlertOnce(restored))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, restored.groupAlertBehavior)
        assertEquals(1L to 1, AlarmForegroundService.ringingAlarm.value)
        assertNull(service.finishedAlarmId.value)
        assertNotNull(restored.contentView)
        assertNotNull(restored.bigContentView)
        assertNull(restored.headsUpContentView)
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

    @Test
    fun snoozeSchedulesOnceAndStopsTheActiveAlarm() {
        val notification = ring(alarm(), remainingSnoozes = 2)
        clickAction(notification, R.id.notification_snooze)

        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val scheduled = alarms.scheduledAlarms.single()
        val intent = shadowOf(scheduled.operation).savedIntent
        assertEquals(1L, intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L))
        assertEquals(1, intent.getIntExtra(AlarmScheduler.EXTRA_REMAINING_SNOOZES, -1))
        assertTrue(intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false))
        assertNull(AlarmForegroundService.ringingAlarm.value)
        assertEquals(1L, service.finishedAlarmId.value)
        assertNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))

        clickAction(notification, R.id.notification_snooze)
        assertSame(scheduled, alarms.scheduledAlarms.single())
    }

    @Test
    fun snoozeAfterStopDoesNotScheduleAnotherAlarm() {
        val notification = ring(alarm())
        clickAction(notification, R.id.notification_stop)
        clickAction(notification, R.id.notification_snooze)

        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertNull(AlarmForegroundService.ringingAlarm.value)
    }

    @Test
    fun staleSnoozeDoesNotScheduleOrStopADifferentAlarm() {
        val old = ring(alarm())
        ring(alarm(id = 2L))
        AlarmStore(context).save(listOf(alarm(), alarm(id = 2L)))

        clickAction(old, R.id.notification_snooze)

        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertEquals(2L to 1, AlarmForegroundService.ringingAlarm.value)
        assertNull(service.finishedAlarmId.value)
    }

    @Test
    fun snoozeDisabledAfterPostingDoesNotScheduleOrStopTheAlarm() {
        val notification = ring(alarm())
        AlarmStore(context).save(listOf(alarm().copy(snoozeEnabled = false)))

        clickAction(notification, R.id.notification_snooze)

        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertEquals(1L to 1, AlarmForegroundService.ringingAlarm.value)
    }

    @Test
    fun exhaustedSnoozesRejectAnEarlierNotificationAction() {
        val old = ring(alarm())
        ring(alarm(), remainingSnoozes = 0)

        clickAction(old, R.id.notification_snooze)

        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertEquals(1L to 0, AlarmForegroundService.ringingAlarm.value)
    }

    @Test
    fun failedSnoozeSchedulingKeepsTheAlarmAndControlsActive() {
        val notification = ring(alarm())
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        clickAction(notification, R.id.notification_snooze)

        assertEquals(1L to 1, AlarmForegroundService.ringingAlarm.value)
        assertNull(service.finishedAlarmId.value)
        assertNotNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
    }

    @Test
    fun unattendedAlarmStopsAtTenMinutesAndLeavesASilentMissedAlarmNotification() {
        val startedAt = System.currentTimeMillis()
        ring(alarm().copy(vibrationEnabled = true, stopButtonDisabled = true))
        val wakeLock = org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock()
        assertTrue(wakeLock.isHeld)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))
        assertEquals(1L to 1, AlarmForegroundService.ringingAlarm.value)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))

        assertNull(AlarmForegroundService.ringingAlarm.value)
        assertEquals(1L, service.finishedAlarmId.value)
        assertFalse(wakeLock.isHeld)
        assertFalse(shadowOf(service.getSystemService(android.os.VibratorManager::class.java).defaultVibrator).isVibrating)
        assertNull(shadowOf(manager).getNotification(AlarmScheduler.NOTIFICATION_ID))
        val notification = manager.activeNotifications.single().notification
        val timeFormat = DateFormat.getTimeFormat(context)
        assertTrue(notification.`when` in startedAt..System.currentTimeMillis())
        assertEquals("Missed alarm: Morning movement", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            context.getString(
                R.string.notification_missed_alarm_detail,
                10,
                timeFormat.format(Date(startedAt)),
                timeFormat.format(Date(notification.`when`)),
            ),
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
        )
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(notification.channelId).importance)
        assertNull(manager.getNotificationChannel(notification.channelId).sound)
        assertFalse(manager.getNotificationChannel(notification.channelId).shouldVibrate())
        assertFalse(NotificationCompat.getOngoing(notification))
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNull(notification.fullScreenIntent)
        assertFalse(shadowOf(notification.contentIntent).savedIntent.hasExtra(AlarmScheduler.EXTRA_ALARM_ID))
    }

    @Test
    fun activityExtendsTheWindowAndNotificationReportsTheWholeRingingDuration() {
        ring(alarm())
        val wakeLock = org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))

        service.recordActivity(1L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))

        assertEquals(1L to 1, AlarmForegroundService.ringingAlarm.value)
        assertTrue(wakeLock.isHeld)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertNull(AlarmForegroundService.ringingAlarm.value)
        assertFalse(wakeLock.isHeld)
        assertTrue(manager.activeNotifications.single().notification.extras
            .getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("Rang for 19 minutes"))
    }

    @Test
    fun automaticPresentationAndStaleActivityDoNotExtendTheWindow() {
        ring(alarm())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))

        service.dismissNotificationPopup(1L)
        service.setRoutineLevel(1L, 1f)
        service.recordActivity(2L)
        service.onStartCommand(
            Intent(context, AlarmForegroundService::class.java).putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
                .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, AlarmForegroundService.ringingOccurrenceToken.value), 0, 2,
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))

        assertNull(AlarmForegroundService.ringingAlarm.value)
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun stoppingOrSnoozingCancelsTheMissedAlarmNotification() {
        for (action in listOf(R.id.notification_stop, R.id.notification_snooze)) {
            val notification = ring(alarm())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))
            clickAction(notification, action)

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))

            assertNull(AlarmForegroundService.ringingAlarm.value)
            assertTrue(manager.activeNotifications.isEmpty())
        }
    }

    @Test
    fun replacingAnAlarmCancelsItsOldTimeoutAndStartsAFreshWindow() {
        ring(alarm())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))
        AlarmStore(context).save(listOf(alarm(id = 2L)))
        service.onStartCommand(
            Intent(context, AlarmForegroundService::class.java).putExtra(AlarmScheduler.EXTRA_ALARM_ID, 2L)
                .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, AlarmOccurrenceStore(context).begin(alarm(id = 2L), 0).token), 0, 2,
        )

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertEquals(2L to 0, AlarmForegroundService.ringingAlarm.value)
        assertEquals(1, manager.activeNotifications.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))

        assertNull(AlarmForegroundService.ringingAlarm.value)
        assertEquals("2", manager.activeNotifications.single().tag)
    }

    @Test
    fun destroyingServiceCancelsItsTimeout() {
        ring(alarm())
        controller.destroy()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(11))

        assertTrue(manager.activeNotifications.none { it.notification.channelId == "missed_alarms" })
        controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        service = controller.get()
    }

    private fun clickAction(notification: Notification, actionId: Int) {
        val application = shadowOf(RuntimeEnvironment.getApplication())
        val broadcastCount = application.broadcastIntents.size
        assertTrue(inflate(notification.bigContentView, 320).findViewById<View>(actionId).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(broadcastCount + 1, application.broadcastIntents.size)
        val intent = application.broadcastIntents.last()
        // Robolectric records explicit broadcasts but does not instantiate these manifest receivers.
        when (intent.component?.className) {
            SnoozeReceiver::class.java.name -> SnoozeReceiver().onReceive(context, intent)
            StopAlarmReceiver::class.java.name -> StopAlarmReceiver().onReceive(context, intent)
            else -> error("Unexpected notification action: $intent")
        }
        application.nextStartedService?.let {
            service.onStartCommand(it, 0, 2)
        }
    }

    private fun alarm(id: Long = 1L) = AlarmConfig.create(id).copy(
        name = "Morning movement", soundEnabled = false, vibrationEnabled = false,
    )

    private fun ring(alarm: AlarmConfig, remainingSnoozes: Int = 1): Notification {
        // A new occurrence is required when changing settings for the same alarm.
        AlarmForegroundService.ringingAlarm.value?.first?.let { service.finishRoutine(it) }
        AlarmStore(context).save(listOf(alarm))
        service.onStartCommand(
            Intent(context, AlarmForegroundService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, AlarmOccurrenceStore(context).begin(alarm, remainingSnoozes).token)
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
