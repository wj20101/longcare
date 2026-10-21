package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
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
            1_000L + testScheduler.currentTime
        }

        assertEquals(901_000L, timer.synchronizeCountdown(order, totalMinutes = 15))
        assertEquals("00:13:00", state.formattedTime.value)
    }

    @Test
    fun `fallback uses an earlier confirmed origin committed during initialization`() = runTest {
        coEvery { repository.getLocalState(order) } returnsMany listOf(
            null, OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = 1_000L),
        )
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { 121_000L }

        assertEquals(901_000L, timer.synchronizeCountdown(order, totalMinutes = 15))
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
            ) { now }
            val expectedDeadline = 1_000L + minutes * 60_000L
            assertEquals(expectedDeadline, timer.synchronizeCountdown(order, minutes))
            now += 60_000L
            assertEquals(expectedDeadline, timer.synchronizeCountdown(order, minutes))
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
            val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { 1_000L }
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
            1_000L + testScheduler.currentTime
        }

        assertEquals(61_000L, timer.synchronizeCountdown(order, totalMinutes = 1))
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
        assertEquals(61_000L, timer.synchronizeCountdown(order, totalMinutes = 1))
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
            1_000L + testScheduler.currentTime
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
        val timer = ServiceCountdownTimerDelegate(state, repository, backgroundScope) { now }

        for (remaining in listOf(999L, 500L, 1L)) {
            now = deadline - remaining
            assertEquals(deadline, timer.synchronizeCountdown(order, totalMinutes = 1))
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
            1_000L + testScheduler.currentTime
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
            1_000L + testScheduler.currentTime
        }
        assertEquals(61_000L, timer.synchronizeCountdown(order, totalMinutes = 1))
        assertEquals(null, state.countdownJob)
        advanceTimeBy(10_000L)
        assertEquals("00:01:00", state.formattedTime.value)

        timer.startDisplayUpdates()
        assertEquals("00:00:50", state.formattedTime.value)
        coVerify(exactly = 1) { repository.getLocalState(order) }
        timer.stopDisplayUpdates()
    }
}
