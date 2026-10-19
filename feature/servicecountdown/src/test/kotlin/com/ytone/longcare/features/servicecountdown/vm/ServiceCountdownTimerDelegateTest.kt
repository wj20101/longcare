package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coVerify(exactly = 0) { repository.startLocalService(any()) }
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
