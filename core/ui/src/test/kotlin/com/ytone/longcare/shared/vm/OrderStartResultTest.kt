package com.ytone.longcare.shared.vm

import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderStartResultTest {
    private val details = mockk<OrderDetailRepository>(relaxed = true)
    private val orders = mockk<OrderRepository>()
    private val location = mockk<LocationFacade>()
    private val lifecycle = mockk<ServiceOrderLifecycle>(relaxed = true)
    private val text = mockk<ResourceTextResolver>(relaxed = true)
    private val key = OrderKey(42, 7)
    private val sample = LocationResult(31.0, 121.0, "test")

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(DiagnosticEventTracker)
        coEvery { location.acquireServiceOrderLocation() } returns LocationAcquisition.Success(sample)
        every { location.serviceOrderLocationFailure(sample) } returns null
        coEvery { orders.starOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
    }

    @After fun tearDown() {
        unmockkObject(DiagnosticEventTracker)
        Dispatchers.resetMain()
    }

    @Test fun `start completed without a UI collector retains its own projects until acknowledged`() = runTest {
        val response = CompletableDeferred<ApiResult<Unit>>()
        coEvery { orders.starOrder(any(), any(), any(), any()) } coAnswers { response.await() }
        val model = model()
        val selected = mutableListOf(3, 9)
        model.starOrder(key, selected)
        runCurrent()
        selected.clear()
        response.complete(ApiResult.Success(Unit))
        runCurrent()

        // A recreated/resumed screen reads the confirmed action from state, without an old callback.
        val result = model.starOrderState.first() as StarOrderUiState.Success
        assertEquals(StarOrderUiState.Success(key, listOf(3, 9)), result)
        coVerify(exactly = 1) { details.updateSelectedProjects(key, listOf(3, 9)) }
        coVerify(exactly = 1) { orders.starOrder(42, listOf(3L, 9L), "121.0", "31.0") }
        verify(exactly = 1) { lifecycle.onOrderStarted(42) }

        model.acknowledgeStartResult(result.copy(orderKey = OrderKey(43)))
        assertEquals(result, model.starOrderState.value)
        model.acknowledgeStartResult(result)
        assertEquals(StarOrderUiState.Initial, model.starOrderState.value)
    }

    @Test fun `disk failure after server start keeps success and cannot submit the start again`() = runTest {
        coEvery { details.updateSelectedProjects(key, any()) } throws IllegalStateException("disk unavailable")
        val model = model()
        model.starOrder(key, listOf(9))
        runCurrent()

        assertEquals(StarOrderUiState.Success(key, listOf(9)), model.starOrderState.value)
        model.starOrder(key, listOf(3))
        runCurrent()
        coVerify(exactly = 1) { orders.starOrder(any(), any(), any(), any()) }
        verify(exactly = 1) { lifecycle.onOrderStarted(42) }
        verify(exactly = 1) {
            DiagnosticEventTracker.trackError(any(), "service_start_projects_save_failed", any(), any(), any(), any())
        }
    }

    @Test fun `confirmed start persists the countdown origin before projects and navigation`() = runTest {
        val response = CompletableDeferred<ApiResult<Unit>>()
        val projectSave = CompletableDeferred<Unit>()
        val persistedStart = slot<Long>()
        coEvery { orders.starOrder(any(), any(), any(), any()) } coAnswers { response.await() }
        coEvery { details.startLocalService(key, capture(persistedStart)) } returns Unit
        coEvery { details.updateSelectedProjects(key, any()) } coAnswers { projectSave.await() }
        val model = model()
        model.starOrder(key, listOf(9))
        runCurrent()
        coVerify(exactly = 0) { details.startLocalService(any(), any()) }

        val beforeResponse = System.currentTimeMillis()
        response.complete(ApiResult.Success(Unit))
        runCurrent()
        val afterPersistence = System.currentTimeMillis()

        // Project persistence and the next page may take minutes; the service has already begun.
        coVerify(exactly = 1) { details.startLocalService(key, any()) }
        assertTrue(persistedStart.captured in beforeResponse..afterPersistence)
        coVerifyOrder {
            details.startLocalService(key, any())
            details.updateSelectedProjects(key, listOf(9))
        }
        assertEquals(StarOrderUiState.Loading, model.starOrderState.value)
        projectSave.complete(Unit)
        runCurrent()
        assertEquals(StarOrderUiState.Success(key, listOf(9)), model.starOrderState.value)
    }

    @Test fun `failed start never creates a local countdown origin`() = runTest {
        coEvery { orders.starOrder(any(), any(), any(), any()) } returns ApiResult.Failure(400, "rejected")
        val model = model()
        model.starOrder(key)
        runCurrent()

        coVerify(exactly = 0) { details.startLocalService(any(), any()) }
        assertTrue(model.starOrderState.value is StarOrderUiState.Error)
    }

    @Test fun `start time persistence failure retains server success and still saves projects`() = runTest {
        coEvery { details.startLocalService(key, any()) } throws IllegalStateException("disk unavailable")
        val model = model()
        model.starOrder(key, listOf(9))
        runCurrent()

        assertEquals(StarOrderUiState.Success(key, listOf(9)), model.starOrderState.value)
        coVerify(exactly = 1) { details.updateSelectedProjects(key, listOf(9)) }
        model.starOrder(key, listOf(3))
        runCurrent()
        coVerify(exactly = 1) { orders.starOrder(any(), any(), any(), any()) }
        verify(exactly = 1) {
            DiagnosticEventTracker.trackError(any(), "service_start_time_save_failed", any(), any(), any(), any())
        }
    }

    @Test fun `changing orders completes confirmed start persistence without publishing old projects or UI`() = runTest {
        val write = CompletableDeferred<Unit>()
        var savedStart: Long? = null
        coEvery { details.startLocalService(key, any()) } coAnswers {
            val confirmedAt = secondArg<Long>()
            write.await()
            savedStart = confirmedAt
        }
        val model = model()
        model.starOrder(key, listOf(3))
        runCurrent()
        val next = OrderKey(43)
        model.starOrder(next, listOf(9))
        runCurrent()
        assertEquals(StarOrderUiState.Success(next, listOf(9)), model.starOrderState.value)

        write.complete(Unit)
        runCurrent()

        // Server success is final even if its screen is replaced during the local Room write.
        assertNotNull(savedStart)
        coVerify(exactly = 0) { details.updateSelectedProjects(key, any()) }
        assertEquals(StarOrderUiState.Success(next, listOf(9)), model.starOrderState.value)
    }

    @Test fun `late cancelled project save cannot replace the next orders pending result`() = runTest {
        val oldSave = CompletableDeferred<Unit>()
        coEvery { details.updateSelectedProjects(key, any()) } coAnswers {
            withContext(NonCancellable) { oldSave.await() }
        }
        val model = model()
        model.starOrder(key, listOf(3))
        runCurrent()
        val next = OrderKey(43)
        model.starOrder(next, listOf(9))
        runCurrent()
        oldSave.complete(Unit)
        runCurrent()

        assertEquals(StarOrderUiState.Success(next, listOf(9)), model.starOrderState.value)
        model.acknowledgeStartResult(StarOrderUiState.Success(key, listOf(3)))
        assertEquals(StarOrderUiState.Success(next, listOf(9)), model.starOrderState.value)
    }

    @Test fun `old snackbar cannot consume a newer failure with the same text`() = runTest {
        coEvery { location.acquireServiceOrderLocation() } returns LocationAcquisition.Failure(LocationFailure.QUALITY)
        every { text.text(any()) } returns "定位失败"
        val model = model()
        model.starOrder(key)
        runCurrent()
        val oldError = model.starOrderState.value as StarOrderUiState.Error
        model.starOrder(key)
        runCurrent()
        val newError = model.starOrderState.value as StarOrderUiState.Error
        assertEquals(oldError, newError)
        assertNotSame(oldError, newError)

        model.acknowledgeStartResult(oldError)
        assertEquals(newError, model.starOrderState.value)
        model.acknowledgeStartResult(newError)
        assertEquals(StarOrderUiState.Initial, model.starOrderState.value)
    }

    private fun model() = SharedOrderDetailViewModel(details, orders, location, text, lifecycle)
}
