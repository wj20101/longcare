package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.data.database.dao.OrderLocationDao
import com.ytone.longcare.data.database.entity.LocationUploadStatus
import com.ytone.longcare.data.database.entity.OrderLocationEntity
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.features.location.core.LocationFacade
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.features.location.provider.LocationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationReportingManagerTest {

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
    }

    @Test
    fun `startReporting should enqueue and upload location successfully`() = runTest {
        val locationFacade = mockk<LocationFacade>()
        val locationStateManager = mockk<LocationStateManager>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val orderLocationDao = mockk<OrderLocationDao>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val request = OrderInfoRequestModel(orderId = 100L, planId = 0)
        val sample = LocationResult(31.2, 121.5, "amap_continuous", 5f)
        val pending = OrderLocationEntity(
            id = 1L,
            orderId = 100L,
            latitude = 31.2,
            longitude = 121.5,
            accuracy = 5f,
            provider = "amap_continuous",
            uploadStatus = LocationUploadStatus.PENDING.value,
            timestamp = System.currentTimeMillis()
        )

        every { locationFacade.observeLocations(any()) } returns flow
        every { locationFacade.acquireKeepAlive(any()) } returns Unit
        every { locationFacade.releaseKeepAlive(any()) } returns Unit
        coEvery { orderLocationDao.insert(any()) } returns 1L
        coEvery { orderLocationDao.getUploadQueue(any(), any()) } returnsMany listOf(
            emptyList(),
            listOf(pending)
        )
        coEvery { orderLocationDao.updateStatus(any(), any()) } returns Unit
        coEvery { orderLocationDao.deleteByStatusBefore(any(), any()) } returns 0
        coEvery { locationRepository.addPosition(any(), any(), any()) } returns ApiResult.Success(Unit)

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            orderLocationDao = orderLocationDao,
            ioDispatcher = dispatcher
        )

        manager.startReporting(request)
        runCurrent()

        assertTrue(manager.isTracking.value)
        assertEquals(request, manager.currentTrackingRequest.value)
        verify { locationStateManager.updateTrackingState(true) }
        verify { locationFacade.acquireKeepAlive("location_report_100") }

        flow.emit(sample)
        runCurrent()

        coVerify(exactly = 1) {
            orderLocationDao.insert(match { it.orderId == 100L && it.latitude == 31.2 && it.longitude == 121.5 })
        }
        coVerify(exactly = 1) { locationRepository.addPosition(100L, 31.2, 121.5) }
        coVerify(exactly = 1) { orderLocationDao.updateStatus(1L, LocationUploadStatus.SUCCESS.value) }

        manager.stopReporting()
        runCurrent()

        assertFalse(manager.isTracking.value)
        assertEquals(null, manager.currentTrackingRequest.value)
        verify { locationFacade.releaseKeepAlive("location_report_100") }
        verify { locationStateManager.updateTrackingState(false) }
    }

    @Test
    fun `failed upload should be marked failed then retried to success`() = runTest {
        val locationFacade = mockk<LocationFacade>()
        val locationStateManager = mockk<LocationStateManager>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val orderLocationDao = mockk<OrderLocationDao>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val request = OrderInfoRequestModel(orderId = 200L, planId = 0)
        val sample = LocationResult(30.0, 120.0, "amap_continuous", 10f)
        val pending = OrderLocationEntity(
            id = 2L,
            orderId = 200L,
            latitude = 30.0,
            longitude = 120.0,
            accuracy = 10f,
            provider = "amap_continuous",
            uploadStatus = LocationUploadStatus.PENDING.value,
            timestamp = System.currentTimeMillis()
        )

        every { locationFacade.observeLocations(any()) } returns flow
        every { locationFacade.acquireKeepAlive(any()) } returns Unit
        every { locationFacade.releaseKeepAlive(any()) } returns Unit
        coEvery { orderLocationDao.insert(any()) } returns 2L
        coEvery { orderLocationDao.getUploadQueue(any(), any()) } returnsMany listOf(
            emptyList(),
            listOf(pending),
            listOf(pending)
        )
        coEvery { orderLocationDao.updateStatus(any(), any()) } returns Unit
        coEvery { orderLocationDao.deleteByStatusBefore(any(), any()) } returns 0
        coEvery { locationRepository.addPosition(any(), any(), any()) } returnsMany listOf(
            ApiResult.Failure(500, "server busy"),
            ApiResult.Success(Unit)
        )

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            orderLocationDao = orderLocationDao,
            ioDispatcher = dispatcher
        )

        manager.startReporting(request)
        runCurrent()

        flow.emit(sample)
        runCurrent()

        flow.emit(sample)
        runCurrent()

        coVerify(exactly = 1) { orderLocationDao.updateStatus(2L, LocationUploadStatus.FAILED.value) }
        coVerify(exactly = 1) { orderLocationDao.updateStatus(2L, LocationUploadStatus.SUCCESS.value) }
        coVerify(exactly = 2) { locationRepository.addPosition(200L, 30.0, 120.0) }

        manager.stopReporting()
        runCurrent()
    }

    @Test
    fun `stopReporting should clear state and release keep alive`() = runTest {
        val locationFacade = mockk<LocationFacade>()
        val locationStateManager = mockk<LocationStateManager>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val orderLocationDao = mockk<OrderLocationDao>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val request = OrderInfoRequestModel(orderId = 300L, planId = 0)

        every { locationFacade.observeLocations(any()) } returns flow
        every { locationFacade.acquireKeepAlive(any()) } returns Unit
        every { locationFacade.releaseKeepAlive(any()) } returns Unit
        coEvery { orderLocationDao.getUploadQueue(any(), any()) } returns emptyList()
        coEvery { orderLocationDao.deleteByStatusBefore(any(), any()) } returns 0

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            orderLocationDao = orderLocationDao,
            ioDispatcher = dispatcher
        )

        manager.startReporting(request)
        runCurrent()
        manager.stopReporting()
        runCurrent()

        assertFalse(manager.isTracking.value)
        assertEquals(null, manager.currentTrackingRequest.value)
        verify { locationFacade.acquireKeepAlive("location_report_300") }
        verify { locationFacade.releaseKeepAlive("location_report_300") }
        verify { locationStateManager.updateTrackingState(false) }
    }
}
