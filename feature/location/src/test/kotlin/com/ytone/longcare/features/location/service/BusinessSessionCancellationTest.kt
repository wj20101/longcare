package com.ytone.longcare.features.location.service

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessSessionCancellationTest {
    private val repository = mockk<OrderRepository>()
    private val keepAlive = mockk<LocationKeepAliveManager>(relaxed = true)
    private var owner = ""

    @Before
    fun setup() {
        KLogger.updateConfig { enabled = false }
        every { keepAlive.acquireOrderTracking(any(), any()) } answers { owner = firstArg() }
    }

    @Test
    fun `business end cancels service job synchronously before requesting Android stop`() = runTest {
        coEvery { repository.getOrderState(1) } returns active(1)
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(true)
        manager.startReporting(OrderKey(1))
        runCurrent()
        val session = requireNotNull(manager.sessionFor(owner, 1))
        val job = backgroundScope.launch(start = CoroutineStart.LAZY) { awaitCancellation() }
        session.attach(job)
        job.start()
        every { keepAlive.release(owner) } answers {
            assertTrue(job.isCancelled)
            assertFalse(session.canUpload())
        }
        manager.onOrderEnded(1)
        verify(exactly = 1) { keepAlive.release(owner) }
        assertNull(manager.sessionFor(owner, 1))
    }

    @Test
    fun `late state response after stop cannot recreate a reporting session`() = runTest {
        val response = CompletableDeferred<ApiResult<ServiceOrderStateModel>>()
        coEvery { repository.getOrderState(1) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(true)
        manager.startReporting(OrderKey(1))
        runCurrent()
        manager.stopReporting()
        response.complete(active(1))
        runCurrent()
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        assertNull(manager.orderState.value)
    }

    @Test
    fun `late old order response cannot end a newly started order`() = runTest {
        val old = CompletableDeferred<ApiResult<ServiceOrderStateModel>>()
        coEvery { repository.getOrderState(1) } coAnswers { withContext(NonCancellable) { old.await() } }
        coEvery { repository.getOrderState(2) } returns active(2)
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(true)
        manager.startReporting(OrderKey(1))
        runCurrent()
        manager.startReporting(OrderKey(2))
        runCurrent()
        old.complete(ApiResult.Success(ServiceOrderStateModel(1, 2)))
        runCurrent()
        assertTrue(requireNotNull(manager.sessionFor(owner, 2)).canUpload())
        verify(exactly = 0) { keepAlive.release(any()) }
        manager.stopReporting()
    }

    @Test
    fun `restart of same order rejects a stale Service command by unique owner`() = runTest {
        coEvery { repository.getOrderState(1) } returns active(1)
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(true)
        manager.startReporting(OrderKey(1))
        runCurrent()
        val oldOwner = owner
        val oldSession = requireNotNull(manager.sessionFor(oldOwner, 1))
        manager.stopReporting()
        manager.startReporting(OrderKey(1))
        runCurrent()
        assertNotEquals(oldOwner, owner)
        assertFalse(oldSession.canUpload())
        assertNull(manager.sessionFor(oldOwner, 1))
        assertTrue(requireNotNull(manager.sessionFor(owner, 1)).canUpload())
        manager.stopReporting()
    }

    private fun active(id: Long): ApiResult<ServiceOrderStateModel> =
        ApiResult.Success(ServiceOrderStateModel(id, 1))

    @Test
    fun `query started before formal start success cannot undo the newer confirmation`() = runTest {
        val old = CompletableDeferred<ApiResult<ServiceOrderStateModel>>()
        coEvery { repository.getOrderState(1) } coAnswers { old.await() }
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(true)
        manager.monitorOrder(1)
        runCurrent()
        manager.onOrderStarted(1)
        manager.startReporting(OrderKey(1))
        old.complete(ApiResult.Success(ServiceOrderStateModel(1, 0)))
        runCurrent()
        assertTrue(requireNotNull(manager.sessionFor(owner, 1)).canUpload())
        verify(exactly = 0) { keepAlive.release(any()) }
        manager.stopReporting()
    }
}
