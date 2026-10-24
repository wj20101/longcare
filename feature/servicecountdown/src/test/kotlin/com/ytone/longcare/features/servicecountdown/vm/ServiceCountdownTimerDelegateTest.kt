package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.ServiceCountdownDeadline
import com.ytone.longcare.model.ServiceTimeSnapshot
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.LocalOrderStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceCountdownTimerDelegateTest {
    private val order = OrderKey(7L, 2)
    private val repository = mockk<OrderDetailRepository>(relaxed = true)
    private val state = ServiceCountdownStateHolder()

    @Test
    fun `fallback origin does not include slow persistence time`() = runTest {
        coEvery { repository.getLocalState(order) } returns null
        coEvery { repository.startLocalService(order, any()) } coAnswers { delay(120_000L) }
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) {
            ServiceTimeSnapshot(1_000L + testScheduler.currentTime, testScheduler.currentTime, 7)
        }

        assertEquals(ServiceCountdownDeadline(901_000L, 900_000L, 7), timer.synchronizeCountdown(order, totalMinutes = 15))
        assertEquals("00:13:00", state.formattedTime.value)
        coVerify(exactly = 1) { repository.startLocalService(order, ServiceTimeSnapshot(1_000L, 0L, 7)) }
    }

    @Test
    fun `fallback uses an earlier confirmed origin committed during initialization`() = runTest {
        coEvery { repository.getLocalState(order) } returnsMany listOf(
            null, OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = 1_000L),
        )
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { ServiceTimeSnapshot(121_000L, 120_000L, null) }

        assertEquals(ServiceCountdownDeadline(901_000L), timer.synchronizeCountdown(order, totalMinutes = 15))
        assertEquals("00:13:00", state.formattedTime.value)
    }

    @Test
    fun `delayed first page and resume preserve fifteen and twenty minute deadlines`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000L)
        for ((minutes, lateMillis) in listOf(15 to 120_000L, 20 to 180_000L)) {
            var now = 1_000L + lateMillis
            val timer = ServiceCountdownTimerDelegate(
                ServiceCountdownStateHolder(), repository, backgroundScope,
            ) { ServiceTimeSnapshot(now, 0L, null) }
            val expectedDeadline = 1_000L + minutes * 60_000L
            assertEquals(ServiceCountdownDeadline(expectedDeadline), timer.synchronizeCountdown(order, minutes))
            now += 60_000L
            assertEquals(ServiceCountdownDeadline(expectedDeadline), timer.synchronizeCountdown(order, minutes))
        }
        coVerify(exactly = 0) { repository.startLocalService(any(), any()) }
    }

    @Test
    fun `deadline origin is reported once and keeps first preparation user across suspension`() = runTest {
        mockkObject(DiagnosticEventTracker)
        try {
            every { DiagnosticEventTracker.currentUserId() } returns "3"
            every { DiagnosticEventTracker.trackEvent(any(), any(), any(), any(), any(), any()) } just runs
            coEvery { repository.getLocalState(order) } coAnswers {
                // A diagnostic must not be attributed to a different login after a disk suspension.
                every { DiagnosticEventTracker.currentUserId() } returns "4"
                null
            }
            val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { ServiceTimeSnapshot(1_000L, 0L, null) }
            timer.synchronizeCountdown(order, totalMinutes = 15)
            coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
                orderId = order.orderId, localStartTimestamp = 1_000L)
            timer.synchronizeCountdown(order, totalMinutes = 15)

            verify(exactly = 1) { DiagnosticEventTracker.trackEvent(
                DiagnosticCategory.COUNTDOWN, "countdown_deadline_prepared", any(),
                match { it["startSource"] == "created_on_countdown" && it["totalMinutes"] == 15 && it["plannedAt"] == 901_000L },
                "3", true) }
            verify(exactly = 1) { DiagnosticEventTracker.trackEvent(any(), any(), any(), any(), any(), any()) }
        } finally { unmockkObject(DiagnosticEventTracker) }
    }

    @Test
    fun `display ticks use the deadline without reading persistence every second`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000L)
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) {
            ServiceTimeSnapshot(1_000L + testScheduler.currentTime, testScheduler.currentTime, null)
        }

        assertEquals(ServiceCountdownDeadline(61_000L), timer.synchronizeCountdown(order, totalMinutes = 1))
        assertEquals("00:01:00", state.formattedTime.value)
        timer.startDisplayUpdates()
        runCurrent()
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(ServiceCountdownState.RUNNING, state.countdownState.value)
        assertEquals("00:00:50", state.formattedTime.value)
        coVerify(exactly = 1) { repository.getLocalState(order) }
        timer.stopDisplayUpdates()
        advanceTimeBy(5_000L)
        assertEquals("00:00:50", state.formattedTime.value)

        // 重新可见只读取一次原起点；后台经过的时间被计入，不把暂停显示当作暂停服务。
        assertEquals(ServiceCountdownDeadline(61_000L), timer.synchronizeCountdown(order, totalMinutes = 1))
        assertEquals(ServiceCountdownState.RUNNING, state.countdownState.value)
        assertEquals("00:00:45", state.formattedTime.value)
        coVerify(exactly = 2) { repository.getLocalState(order) }
        coVerify(exactly = 0) { repository.startLocalService(any(), any()) }
        timer.stopDisplayUpdates()
    }

    @Test
    fun `crossing the deadline enters overtime without ending the business order`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000L)
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) {
            ServiceTimeSnapshot(1_000L + testScheduler.currentTime, testScheduler.currentTime, null)
        }
        timer.synchronizeCountdown(order, totalMinutes = 1)
        timer.startDisplayUpdates()
        runCurrent()
        advanceTimeBy(61_001L)
        runCurrent()

        assertEquals(ServiceCountdownState.OVERTIME, state.countdownState.value)
        assertEquals("00:00:01", state.formattedTime.value)
        coVerify(exactly = 1) { repository.getLocalState(order) }
        coVerify(exactly = 0) { repository.endLocalService(any()) }
        timer.stopDisplayUpdates()
    }

    @Test
    fun `last fractional second stays visible until deadline without rounding overtime up`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000L)
        val deadline = 61_000L
        var now = deadline - 999L
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { ServiceTimeSnapshot(now, 0L, null) }

        for (remaining in listOf(999L, 500L, 1L)) {
            now = deadline - remaining
            assertEquals(ServiceCountdownDeadline(deadline), timer.synchronizeCountdown(order, totalMinutes = 1))
            assertEquals(ServiceCountdownState.RUNNING, state.countdownState.value)
            assertEquals("00:00:01", state.formattedTime.value)
        }

        now = deadline
        timer.synchronizeCountdown(order, totalMinutes = 1)
        assertEquals(ServiceCountdownState.OVERTIME, state.countdownState.value)
        assertEquals("00:00:00", state.formattedTime.value)

        now = deadline + 999L
        timer.synchronizeCountdown(order, totalMinutes = 1)
        assertEquals(ServiceCountdownState.OVERTIME, state.countdownState.value)
        assertEquals("00:00:00", state.formattedTime.value)
        now = deadline + 1_000L
        timer.synchronizeCountdown(order, totalMinutes = 1)
        assertEquals(ServiceCountdownState.OVERTIME, state.countdownState.value)
        assertEquals("00:00:01", state.formattedTime.value)
        coVerify(exactly = 0) { repository.endLocalService(any()) }
        timer.stopDisplayUpdates()
    }

    @Test
    fun `resynchronizing replaces the old display job`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000L)
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) {
            ServiceTimeSnapshot(1_000L + testScheduler.currentTime, testScheduler.currentTime, null)
        }
        timer.synchronizeCountdown(order, totalMinutes = 1)
        timer.startDisplayUpdates()
        val firstDisplayJob = requireNotNull(state.countdownJob)
        timer.synchronizeCountdown(order, totalMinutes = 1)

        assertTrue(firstDisplayJob.isCancelled)
        assertTrue(state.countdownJob !== firstDisplayJob)
        coVerify(exactly = 2) { repository.getLocalState(order) }
        timer.stopDisplayUpdates()
    }

    @Test
    fun `preparing in background does not start a ticker and resume uses the original deadline`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000L)
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) {
            ServiceTimeSnapshot(1_000L + testScheduler.currentTime, testScheduler.currentTime, null)
        }
        assertEquals(ServiceCountdownDeadline(61_000L), timer.synchronizeCountdown(order, totalMinutes = 1))
        assertEquals(null, state.countdownJob)
        advanceTimeBy(10_000L)
        assertEquals("00:01:00", state.formattedTime.value)

        timer.startDisplayUpdates()
        assertEquals("00:00:50", state.formattedTime.value)
        coVerify(exactly = 1) { repository.getLocalState(order) }
        timer.stopDisplayUpdates()
    }

    @Test
    fun `same boot clock corrections preserve remaining duration and reminder identity`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000_000L,
            localStartElapsedRealtime = 100_000L, localStartBootCount = 7,
        )
        var now = ServiceTimeSnapshot(1_060_000L, 160_000L, 7)
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { now }
        val deadline = ServiceCountdownDeadline(1_900_000L, 1_000_000L, 7)

        for (wallShift in listOf(0L, 300_000L, -300_000L)) {
            now = now.copy(wallTimeMillis = 1_060_000L + wallShift)
            assertEquals(deadline, timer.synchronizeCountdown(order, 15))
            assertEquals("00:14:00", state.formattedTime.value)
            assertEquals(840_000L, deadline.remainingMillis(now))
            assertEquals(1_900_000L + wallShift, deadline.triggerAtMillis(now))
        }
        coVerify(exactly = 0) { repository.startLocalService(any(), any()) }
    }

    @Test
    fun `recreated countdown includes background elapsed time after wall clock rollback`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000_000L,
            localStartElapsedRealtime = 100_000L, localStartBootCount = 7,
        )
        var now = ServiceTimeSnapshot(1_000_000L, 100_000L, 7)
        val first = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { now }
        val original = first.synchronizeCountdown(order, 15)
        first.stopDisplayUpdates()

        // The display process is recreated after ten minutes, including device sleep.
        now = ServiceTimeSnapshot(1_300_000L, 700_000L, 7)
        val restoredState = ServiceCountdownStateHolder()
        val restored = ServiceCountdownTimerDelegate(restoredState, repository, backgroundScope) { now }
        assertEquals(original, restored.synchronizeCountdown(order, 15))
        assertEquals("00:05:00", restoredState.formattedTime.value)
        assertEquals(null, restoredState.countdownJob)
        coVerify(exactly = 0) { repository.startLocalService(any(), any()) }
    }

    @Test
    fun `a different or unknown boot falls back to the original wall deadline`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000_000L,
            localStartElapsedRealtime = 100_000L, localStartBootCount = 7,
        )
        for (boot in listOf(8, null)) {
            val now = ServiceTimeSnapshot(1_840_000L, 10_000L, boot)
            val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { now }
            val deadline = requireNotNull(timer.synchronizeCountdown(order, 15))
            assertEquals("00:01:00", state.formattedTime.value)
            assertEquals(60_000L, deadline.remainingMillis(now))
            assertEquals(1_900_000L, deadline.triggerAtMillis(now))
        }
    }

    @Test
    fun `legacy wall origin is not reinterpreted as a same boot elapsed origin`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStartTimestamp = 1_000_000L,
        )
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) {
            ServiceTimeSnapshot(1_840_000L, 10_000L, 7)
        }
        assertEquals(ServiceCountdownDeadline(1_900_000L), timer.synchronizeCountdown(order, 15))
        assertEquals("00:01:00", state.formattedTime.value)
        coVerify(exactly = 0) { repository.startLocalService(any(), any()) }
    }

    @Test
    fun `completed local record with a future deadline cannot restart a countdown`() = runTest {
        coEvery { repository.getLocalState(order) } returns OrderLocalStateEntity(
            orderId = order.orderId, localStatus = LocalOrderStatus.COMPLETED.value,
            localStartTimestamp = 1_000_000L, localStartElapsedRealtime = 100_000L, localStartBootCount = 7,
        )
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) {
            ServiceTimeSnapshot(1_060_000L, 160_000L, 7)
        }
        timer.startDisplayUpdates()

        assertEquals(null, timer.synchronizeCountdown(order, 15))
        assertEquals(ServiceCountdownState.ENDED, state.countdownState.value)
        assertEquals(null, state.countdownJob)
        coVerify(exactly = 0) { repository.startLocalService(any(), any()) }
        coVerify(exactly = 0) { repository.endLocalService(any()) }
    }

    @Test
    fun `completion committed during fallback persistence wins even without a stored start`() = runTest {
        coEvery { repository.getLocalState(order) } returnsMany listOf(
            null, OrderLocalStateEntity(orderId = order.orderId, localStatus = LocalOrderStatus.COMPLETED.value),
        )
        val now = ServiceTimeSnapshot(1_000_000L, 100_000L, 7)
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { now }
        timer.startDisplayUpdates()

        assertEquals(null, timer.synchronizeCountdown(order, 15))
        assertEquals(ServiceCountdownState.ENDED, state.countdownState.value)
        assertEquals(null, state.countdownJob)
        coVerify(exactly = 1) { repository.startLocalService(order, now) }
        coVerify(exactly = 2) { repository.getLocalState(order) }
        coVerify(exactly = 0) { repository.endLocalService(any()) }
    }
}
