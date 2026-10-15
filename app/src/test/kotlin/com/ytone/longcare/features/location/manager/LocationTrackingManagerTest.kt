package com.ytone.longcare.features.location.manager

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.features.location.reporting.LocationReportingStatus
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationTrackingManagerTest {
    private val keepAlive = mockk<LocationKeepAliveManager>(relaxed = true)
    private val repository = mockk<OrderRepository>()
    private val recoveryStore = mockk<LocationRecoveryStore>(relaxed = true)

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
        every { keepAlive.acquireOrderTracking(any(), any()) } returns true
        coEvery { recoveryStore.read() } returns null
        coEvery { repository.getOrderState(any()) } coAnswers {
            ApiResult.Success(ServiceOrderStateModel(firstArg(), ServiceOrderStateModel.STATE_IN_PROGRESS))
        }
    }

    @Test
    fun `tracking cannot start before a logged in session is known`() = runTest {
        val manager = createManager(backgroundScope)

        manager.startTracking(OrderKey(100L))
        runCurrent()

        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        coVerify(exactly = 0) { repository.getOrderState(any()) }
        assertEquals(LocationReportingStatus.IDLE, manager.status.value)
    }

    @Test
    fun `logged in session allows start and logout stops then rejects restart`() = runTest {
        val owner = slot<String>()
        every { keepAlive.acquireOrderTracking(capture(owner), any()) } returns true
        val manager = createManager(backgroundScope)
        manager.onSessionStateChanged(loggedIn(accountId = 2))
        manager.startTracking(OrderKey(100L))
        runCurrent()
        assertTrue(owner.isCaptured)

        manager.onSessionStateChanged(SessionState.LoggedOut)
        manager.startTracking(OrderKey(200L))
        runCurrent()

        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), 100L) }
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), 200L) }
        verify(exactly = 1) { keepAlive.release(owner.captured) }
        coVerify(exactly = 0) { repository.getOrderState(200L) }
        assertEquals(LocationReportingStatus.IDLE, manager.status.value)
        assertNull(manager.pendingRecovery.value)
    }

    @Test
    fun `unknown session pauses new starts until identity is available again`() = runTest {
        val manager = createManager(backgroundScope)
        manager.onSessionStateChanged(loggedIn(accountId = 2))
        manager.onSessionStateChanged(SessionState.Unknown)

        manager.startTracking(OrderKey(100L))
        runCurrent()

        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        coVerify(exactly = 0) { repository.getOrderState(any()) }

        manager.onSessionStateChanged(loggedIn(accountId = 2))
        manager.startTracking(OrderKey(100L))
        runCurrent()
        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), 100L) }
    }

    @Test
    fun `account switch releases previous owner and uses the new identity on explicit start`() = runTest {
        val owners = mutableListOf<String>()
        every { keepAlive.acquireOrderTracking(capture(owners), any()) } returns true
        val saved = mutableListOf<LocationRecoveryRecord>()
        coEvery { recoveryStore.save(capture(saved)) } returns Unit
        val manager = createManager(backgroundScope)
        manager.onSessionStateChanged(loggedIn(accountId = 2))
        manager.startTracking(OrderKey(100L))
        runCurrent()

        manager.onSessionStateChanged(loggedIn(accountId = 3))
        runCurrent()

        verify(exactly = 1) { keepAlive.release(owners.first()) }
        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), any()) }
        assertEquals(LocationReportingStatus.IDLE, manager.status.value)

        manager.startTracking(OrderKey(200L))
        runCurrent()

        assertEquals(2, owners.size)
        assertNotEquals(owners.first(), owners.last())
        assertEquals(LocationAccount(1, 3, 3), saved.last().account)
        assertEquals(200L, saved.last().orderId)
        assertEquals(owners.last(), saved.last().sessionId)
    }

    @Test
    fun `logout is serialized after an in progress start and wins final state`() = runTest {
        val startEntered = CountDownLatch(1)
        val allowStartToFinish = CountDownLatch(1)
        val logoutEntered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        every { keepAlive.acquireOrderTracking(any(), any()) } answers {
            events += "start-begin"
            startEntered.countDown()
            check(allowStartToFinish.await(2, TimeUnit.SECONDS))
            events += "start-end"
            true
        }
        every { keepAlive.release(any()) } answers { events += "stop" }
        val manager = createManager(backgroundScope)
        manager.onSessionStateChanged(loggedIn(accountId = 2))
        runCurrent()
        val executor = Executors.newFixedThreadPool(2)
        try {
            // Block the actual service dispatch while logout competes for the reporting lock.
            val start = executor.submit { manager.startTracking(OrderKey(100L)) }
            assertTrue(startEntered.await(2, TimeUnit.SECONDS))
            val logout = executor.submit {
                logoutEntered.countDown()
                manager.onSessionStateChanged(SessionState.LoggedOut)
            }
            assertTrue(logoutEntered.await(2, TimeUnit.SECONDS))
            allowStartToFinish.countDown()
            start.get(2, TimeUnit.SECONDS)
            logout.get(2, TimeUnit.SECONDS)
            manager.startTracking(OrderKey(200L))
            runCurrent()

            assertEquals(listOf("start-begin", "start-end", "stop"), events)
            verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), any()) }
            coVerify(exactly = 0) { repository.getOrderState(any()) }
            assertEquals(LocationReportingStatus.IDLE, manager.status.value)
        } finally {
            allowStartToFinish.countDown()
            executor.shutdownNow()
        }
    }

    private fun createManager(scope: CoroutineScope) = LocationTrackingManager(
        LocationReportingManager(keepAlive, repository, scope, recoveryStore),
    )

    private fun loggedIn(accountId: Int) = SessionState.LoggedIn(
        User(companyId = 1, accountId = accountId, userId = accountId),
    )
}
