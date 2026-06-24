package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.model.LocationUploadStatus
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderLocationEntity
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.domain.location.LocationUploadQueueRepository
import com.ytone.longcare.features.location.manager.LocationStateManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
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
        val queueRepository = mockk<LocationUploadQueueRepository>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val orderKey = OrderKey(orderId = 100L, planId = 0)
        val sampleTime = System.currentTimeMillis()
        val sample = LocationResult(
            latitude = 31.2,
            longitude = 121.5,
            provider = "amap_continuous",
            accuracy = 5f,
            coordType = "GCJ02",
            locationType = 5,
            trustedLevel = 2,
            locationTime = sampleTime
        )
        val pending = OrderLocationEntity(
            id = 1L,
            orderId = 100L,
            latitude = 31.2,
            longitude = 121.5,
            accuracy = 5f,
            provider = "amap_continuous",
            coordType = "GCJ02",
            locationType = 5,
            trustedLevel = 2,
            locationTime = sampleTime,
            uploadStatus = LocationUploadStatus.PENDING.value,
            timestamp = System.currentTimeMillis()
        )

        every { locationFacade.observeLocations(any()) } returns flow
        every { locationFacade.acquireKeepAlive(any()) } returns Unit
        every { locationFacade.releaseKeepAlive(any()) } returns Unit
        coEvery { queueRepository.insert(any()) } returns 1L
        coEvery { queueRepository.getUploadQueue(any(), any()) } returnsMany listOf(
            emptyList(),
            listOf(pending)
        )
        coEvery { queueRepository.updateStatus(any(), any()) } returns Unit
        coEvery { queueRepository.deleteByStatusBefore(any(), any()) } returns 0
        coEvery { locationRepository.addPosition(any(), any(), any()) } returns ApiResult.Success(Unit)

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            locationUploadQueueRepository = queueRepository,
            ioDispatcher = dispatcher
        )

        manager.startReporting(orderKey)
        runCurrent()

        assertTrue(manager.isTracking.value)
        assertEquals(orderKey, manager.currentTrackingOrderKey.value)
        verify { locationStateManager.startTracking(orderKey) }
        verify { locationFacade.acquireKeepAlive("location_report_100") }

        flow.emit(sample)
        runCurrent()

        coVerify(exactly = 1) {
            queueRepository.insert(match {
                it.orderId == 100L &&
                    it.latitude == 31.2 &&
                    it.longitude == 121.5 &&
                    it.coordType == "GCJ02" &&
                    it.locationType == 5 &&
                    it.trustedLevel == 2 &&
                    it.locationTime == sampleTime
            })
        }
        coVerify(exactly = 1) { locationRepository.addPosition(100L, 31.2, 121.5) }
        coVerify(exactly = 1) { queueRepository.updateStatus(1L, LocationUploadStatus.SUCCESS.value) }

        manager.stopReporting()
        runCurrent()

        assertFalse(manager.isTracking.value)
        assertEquals(null, manager.currentTrackingOrderKey.value)
        verify { locationFacade.releaseKeepAlive("location_report_100") }
        verify { locationStateManager.stopTracking() }
    }

    @Test
    fun `failed upload should be marked failed then retried to success`() = runTest {
        val locationFacade = mockk<LocationFacade>()
        val locationStateManager = mockk<LocationStateManager>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val queueRepository = mockk<LocationUploadQueueRepository>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val orderKey = OrderKey(orderId = 200L, planId = 0)
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
        coEvery { queueRepository.insert(any()) } returns 2L
        coEvery { queueRepository.getUploadQueue(any(), any()) } returnsMany listOf(
            emptyList(),
            listOf(pending),
            listOf(pending)
        )
        coEvery { queueRepository.updateStatus(any(), any()) } returns Unit
        coEvery { queueRepository.deleteByStatusBefore(any(), any()) } returns 0
        coEvery { locationRepository.addPosition(any(), any(), any()) } returnsMany listOf(
            ApiResult.Failure(500, "server busy"),
            ApiResult.Success(Unit)
        )

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            locationUploadQueueRepository = queueRepository,
            ioDispatcher = dispatcher
        )

        manager.startReporting(orderKey)
        runCurrent()

        flow.emit(sample)
        runCurrent()

        flow.emit(sample)
        runCurrent()

        coVerify(exactly = 1) { queueRepository.updateStatus(2L, LocationUploadStatus.FAILED.value) }
        coVerify(exactly = 1) { queueRepository.updateStatus(2L, LocationUploadStatus.SUCCESS.value) }
        coVerify(exactly = 2) { locationRepository.addPosition(200L, 30.0, 120.0) }

        manager.stopReporting()
        runCurrent()
    }

    @Test
    fun `stale replayed location should be skipped before enqueue`() = runTest {
        val locationFacade = mockk<LocationFacade>()
        val locationStateManager = mockk<LocationStateManager>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val queueRepository = mockk<LocationUploadQueueRepository>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val orderKey = OrderKey(orderId = 250L, planId = 0)
        val staleLocation = LocationResult(
            latitude = 30.0,
            longitude = 120.0,
            provider = "amap_continuous",
            accuracy = 10f,
            locationTime = System.currentTimeMillis() - 5 * 60 * 1000L
        )
        val freshLocation = staleLocation.copy(
            latitude = 30.1,
            longitude = 120.1,
            locationTime = System.currentTimeMillis()
        )

        every { locationFacade.observeLocations(any()) } returns flow
        every { locationFacade.acquireKeepAlive(any()) } returns Unit
        every { locationFacade.releaseKeepAlive(any()) } returns Unit
        coEvery { queueRepository.insert(any()) } returns 5L
        coEvery { queueRepository.getUploadQueue(any(), any()) } returns emptyList()
        coEvery { queueRepository.deleteByStatusBefore(any(), any()) } returns 0

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            locationUploadQueueRepository = queueRepository,
            ioDispatcher = dispatcher
        )

        manager.startReporting(orderKey)
        runCurrent()

        flow.emit(staleLocation)
        runCurrent()

        coVerify(exactly = 0) { queueRepository.insert(any()) }

        flow.emit(freshLocation)
        runCurrent()

        coVerify(exactly = 1) {
            queueRepository.insert(match { it.orderId == 250L && it.latitude == 30.1 && it.longitude == 120.1 })
        }

        manager.stopReporting()
        runCurrent()
    }

    @Test
    fun `stopReporting should clear state and release keep alive`() = runTest {
        val locationFacade = mockk<LocationFacade>()
        val locationStateManager = mockk<LocationStateManager>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val queueRepository = mockk<LocationUploadQueueRepository>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val orderKey = OrderKey(orderId = 300L, planId = 0)

        every { locationFacade.observeLocations(any()) } returns flow
        every { locationFacade.acquireKeepAlive(any()) } returns Unit
        every { locationFacade.releaseKeepAlive(any()) } returns Unit
        coEvery { queueRepository.getUploadQueue(any(), any()) } returns emptyList()
        coEvery { queueRepository.deleteByStatusBefore(any(), any()) } returns 0

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            locationUploadQueueRepository = queueRepository,
            ioDispatcher = dispatcher
        )

        manager.startReporting(orderKey)
        runCurrent()
        manager.stopReporting()
        runCurrent()

        assertFalse(manager.isTracking.value)
        assertEquals(null, manager.currentTrackingOrderKey.value)
        verify { locationFacade.acquireKeepAlive("location_report_300") }
        verify { locationFacade.releaseKeepAlive("location_report_300") }
        verify { locationStateManager.startTracking(orderKey) }
        verify { locationStateManager.stopTracking() }
    }

    @Test
    fun `cancellation during upload should not mark record as failed`() = runTest {
        val locationFacade = mockk<LocationFacade>()
        val locationStateManager = mockk<LocationStateManager>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val queueRepository = mockk<LocationUploadQueueRepository>()
        val flow = MutableSharedFlow<LocationResult>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val orderKey = OrderKey(orderId = 400L, planId = 0)
        val sample = LocationResult(30.1, 120.1, "amap_continuous", 8f)
        val pending = OrderLocationEntity(
            id = 4L,
            orderId = 400L,
            latitude = 30.1,
            longitude = 120.1,
            accuracy = 8f,
            provider = "amap_continuous",
            uploadStatus = LocationUploadStatus.PENDING.value,
            timestamp = System.currentTimeMillis()
        )

        every { locationFacade.observeLocations(any()) } returns flow
        every { locationFacade.acquireKeepAlive(any()) } returns Unit
        every { locationFacade.releaseKeepAlive(any()) } returns Unit
        coEvery { queueRepository.insert(any()) } returns 4L
        coEvery { queueRepository.getUploadQueue(any(), any()) } returnsMany listOf(
            emptyList(),
            listOf(pending)
        )
        coEvery { queueRepository.deleteByStatusBefore(any(), any()) } returns 0
        coEvery { queueRepository.updateStatus(any(), any()) } returns Unit
        coEvery { locationRepository.addPosition(any(), any(), any()) } throws CancellationException("cancel")

        val manager = LocationReportingManager(
            locationFacade = locationFacade,
            locationStateManager = locationStateManager,
            locationRepository = locationRepository,
            locationUploadQueueRepository = queueRepository,
            ioDispatcher = dispatcher
        )

        manager.startReporting(orderKey)
        runCurrent()

        flow.emit(sample)
        runCurrent()

        coVerify(exactly = 0) { queueRepository.updateStatus(4L, LocationUploadStatus.FAILED.value) }
        coVerify(exactly = 0) { queueRepository.updateStatus(4L, LocationUploadStatus.SUCCESS.value) }

        manager.stopReporting()
        runCurrent()
    }
}
