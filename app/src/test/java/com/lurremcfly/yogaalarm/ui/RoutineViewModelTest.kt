package com.lurremcfly.yogaalarm.ui

import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.BodyLandmarks
import com.lurremcfly.yogaalarm.model.BodyPoint
import com.lurremcfly.yogaalarm.model.PoseFrame
import com.lurremcfly.yogaalarm.model.PoseStep
import com.lurremcfly.yogaalarm.model.YogaPose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private lateinit var viewModel: RoutineViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        viewModel = RoutineViewModel { scheduler.currentTime + 1_000 }
    }

    @After
    fun tearDown() {
        viewModel.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun startupWithoutFramesOffersRecoveryInsteadOfWaitingForever() = routineTest {
        viewModel.start(alarm())
        advanceTimeBy(10_100)
        assertNotNull(viewModel.uiState.value.error)
        assertEquals(0f, viewModel.progress.value.hold)
        viewModel.onCameraRetry()
        assertNull(viewModel.uiState.value.error)
        feedFor(1_000)
        assertEquals(RoutinePhase.HOLDING, viewModel.uiState.value.phase)
    }

    @Test
    fun missingFramesPauseProgressAndDoNotCompleteTheRoutine() = routineTest {
        viewModel.start(alarm())
        feedFor(2_000)
        assertEquals(RoutinePhase.HOLDING, viewModel.uiState.value.phase)
        advanceTimeBy(1_000)
        val pausedAt = viewModel.progress.value.hold
        assertEquals(RoutinePhase.PAUSED, viewModel.uiState.value.phase)
        advanceTimeBy(30_000)
        assertEquals(pausedAt, viewModel.progress.value.hold)
        assertFalse(viewModel.uiState.value.cameraReady)
        assertNull(viewModel.frames.value)
        viewModel.stop()
    }

    @Test
    fun pausePreservesTimeAndRequiresFreshDetectionAfterResume() = routineTest {
        viewModel.start(alarm())
        feedFor(2_000)
        viewModel.pause()
        val pausedAt = viewModel.progress.value.hold
        feedFor(2_000)
        assertEquals(pausedAt, viewModel.progress.value.hold)
        viewModel.resume()
        advanceTimeBy(2_000)
        assertEquals(pausedAt, viewModel.progress.value.hold)
        feedFor(1_000)
        assertTrue(viewModel.progress.value.hold > pausedAt)
        viewModel.stop()
    }

    @Test
    fun stopCancelsTickerAndIgnoresLateFrames() = routineTest {
        viewModel.start(alarm())
        feedFor(2_000)
        viewModel.stop()
        val stopped = viewModel.uiState.value
        feedFor(30_000)
        assertEquals(stopped, viewModel.uiState.value)
        assertNull(viewModel.frames.value)
        assertFalse(stopped.started)
        viewModel.stop()
    }

    @Test
    fun retryPreservesAccumulatedTimeButResetsTracking() = routineTest {
        viewModel.start(alarm())
        feedFor(2_000)
        val progress = viewModel.progress.value.hold
        viewModel.onCameraError("Camera disconnected")
        assertEquals(RoutinePhase.PAUSED, viewModel.uiState.value.phase)
        viewModel.onCameraRetry()
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.detected)
        advanceTimeBy(2_000)
        assertEquals(progress, viewModel.progress.value.hold)
        feedFor(1_000)
        assertTrue(viewModel.progress.value.hold > progress)
        viewModel.stop()
    }

    @Test
    fun completionStopsUpdatesAndReturnsZeroVolume() = routineTest {
        viewModel.start(alarm().copy(routine = listOf(PoseStep(YogaPose.MOUNTAIN, 1))))
        feedFor(3_000)
        assertEquals(RoutinePhase.COMPLETE, viewModel.uiState.value.phase)
        assertEquals(0f, viewModel.progress.value.alarmLevel)
        val completed = viewModel.uiState.value
        feedFor(10_000)
        assertEquals(completed, viewModel.uiState.value)
        viewModel.stop()
    }

    @Test
    fun transitionAnnouncesNextPoseWithoutCreditingOldPoseFrames() = routineTest {
        viewModel.start(alarm().copy(routine = listOf(PoseStep(YogaPose.MOUNTAIN, 1), PoseStep(YogaPose.TREE, 20))))
        feedFor(2_000)
        assertEquals(RoutinePhase.TRANSITION, viewModel.uiState.value.phase)
        assertEquals(YogaPose.TREE, viewModel.uiState.value.nextPose)
        feedFor(4_000)
        assertEquals(YogaPose.TREE, viewModel.uiState.value.pose)
        assertEquals(0f, viewModel.progress.value.hold)
        viewModel.stop()
    }

    @Test
    fun restartingSameConfigurationPreservesProgressAcrossRecreation() = routineTest {
        val config = alarm()
        viewModel.start(config)
        feedFor(2_000)
        val progress = viewModel.progress.value.hold
        viewModel.pause()
        viewModel.start(config)
        assertEquals(progress, viewModel.progress.value.hold)
        viewModel.stop()
    }

    @Test
    fun delayedResultsCannotResumeProgress() = routineTest {
        viewModel.start(alarm())
        feedFor(2_000)
        scheduler.advanceTimeBy(2_000)
        val progress = viewModel.progress.value.hold
        repeat(30) {
            viewModel.onPoseFrame(PoseFrame(mountain(), 2_000, 480, 640, scheduler.currentTime - 1_000))
            scheduler.advanceTimeBy(67)
        }
        assertEquals(progress, viewModel.progress.value.hold)
        assertFalse(viewModel.uiState.value.detected)
        viewModel.stop()
    }

    @Test
    fun smoothingHasSimilarEntryTimingAt15And30Fps() = routineTest {
        fun timeUntilHolding(interval: Long): Long {
            viewModel.stop()
            viewModel.start(alarm())
            val start = scheduler.currentTime
            while (viewModel.uiState.value.phase != RoutinePhase.HOLDING && scheduler.currentTime - start < 2_000) {
                feedFor(interval, interval)
            }
            return scheduler.currentTime - start
        }
        val faster = timeUntilHolding(33)
        val slower = timeUntilHolding(67)
        assertTrue("30fps=$faster ms, 15fps=$slower ms", kotlin.math.abs(faster - slower) <= 150)
        assertTrue(slower < 1_000)
        viewModel.stop()
    }

    @Test
    fun walkingOutOfFramePausesHoldWithinHalfASecond() = routineTest {
        viewModel.start(alarm())
        feedFor(2_000)
        assertEquals(RoutinePhase.HOLDING, viewModel.uiState.value.phase)
        val closeUp = BodyLandmarks(mountain().points.mapIndexed { index, point ->
            if (index >= 23) point.copy(y = 1.5f, visibility = 0.1f) else point
        })
        feedFor(500, body = closeUp)
        assertFalse(viewModel.uiState.value.detected)
        assertEquals(RoutinePhase.PAUSED, viewModel.uiState.value.phase)
        val pausedAt = viewModel.progress.value.hold
        feedFor(2_000, body = closeUp)
        assertEquals(pausedAt, viewModel.progress.value.hold)
        feedFor(1_000)
        assertEquals(RoutinePhase.HOLDING, viewModel.uiState.value.phase)
    }

    @Test
    fun scheduledHourIsAvailableForCompletionGreeting() = routineTest {
        viewModel.start(alarm().copy(hour = 15, routine = listOf(PoseStep(YogaPose.MOUNTAIN, 1))))
        feedFor(3_000)
        assertEquals(RoutinePhase.COMPLETE, viewModel.uiState.value.phase)
        assertEquals(15, viewModel.uiState.value.alarmHour)
    }

    private fun routineTest(block: suspend TestScope.() -> Unit) = runTest(scheduler) {
        try {
            block()
        } finally {
            viewModel.stop()
        }
    }

    private fun feedFor(duration: Long, interval: Long = 67, body: BodyLandmarks = mountain()) {
        val end = scheduler.currentTime + duration
        while (scheduler.currentTime < end) {
            viewModel.onPoseFrame(PoseFrame(body, 10, 480, 640, scheduler.currentTime + 1_000))
            scheduler.advanceTimeBy(minOf(interval, end - scheduler.currentTime))
            scheduler.runCurrent()
        }
    }

    private fun alarm() = AlarmConfig.create(1).copy(routine = listOf(PoseStep(YogaPose.MOUNTAIN, 20)))

    private fun mountain(): BodyLandmarks {
        val points = MutableList(33) { BodyPoint(0.5f, 0.5f, 0f, 1f, 1f) }
        fun point(index: Int, x: Float, y: Float) { points[index] = BodyPoint(x, y, 0f, 1f, 1f) }
        point(0, 0.5f, 0.1f)
        point(11, 0.4f, 0.28f); point(12, 0.6f, 0.28f)
        point(13, 0.39f, 0.48f); point(14, 0.61f, 0.48f)
        point(15, 0.38f, 0.68f); point(16, 0.62f, 0.68f)
        point(23, 0.44f, 0.62f); point(24, 0.56f, 0.62f)
        point(25, 0.44f, 0.9f); point(26, 0.56f, 0.9f)
        return BodyLandmarks(points)
    }
}
