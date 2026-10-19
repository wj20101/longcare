package com.ytone.longcare.features.photoupload.vm

import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.system.WatermarkConfigProvider
import com.ytone.longcare.features.photoupload.ui.refreshCameraOnResume
import com.ytone.longcare.model.LocationResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    @Test fun `resume supports precise approximate denied and restored permission`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val provider = mockk<WatermarkConfigProvider>()
            coEvery { provider.getSyLogoImg() } returns ""
            val location = mockk<LocationFacade>()
            coEvery { location.acquireCurrentLocation() } returns LocationAcquisition.Success(LocationResult(latitude = 30.0, longitude = 120.0, provider = "test"))
            val model = CameraViewModel(provider, location, mockk())
            refreshCameraOnResume(model, fineLocationGranted = true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Coordinates(LocationResult(30.0, 120.0, "test")), model.location.value)
            refreshCameraOnResume(model, fineLocationGranted = false)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Unavailable, model.location.value)
            refreshCameraOnResume(model, fineLocationGranted = false)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Unavailable, model.location.value)
            coVerify(exactly = 1) { location.acquireCurrentLocation() }
            refreshCameraOnResume(model, fineLocationGranted = true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Coordinates(LocationResult(30.0, 120.0, "test")), model.location.value)
            coVerify(exactly = 2) { location.acquireCurrentLocation() }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `revoking permission ignores a late noncancellable location response`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val location = mockk<LocationFacade>()
            coEvery { location.acquireCurrentLocation() } coAnswers {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    kotlinx.coroutines.delay(100)
                    LocationAcquisition.Success(LocationResult(latitude = 30.0, longitude = 120.0, provider = "test"))
                }
            }
            val model = CameraViewModel(mockk(), location, mockk())
            model.updateCurrentLocationInfo(true)
            runCurrent()
            model.updateCurrentLocationInfo(false)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Unavailable, model.location.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `location failure can recover on next resume`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val location = mockk<LocationFacade>()
            coEvery { location.acquireCurrentLocation() } throws IllegalStateException("disabled")
            val model = CameraViewModel(mockk(), location, mockk())
            model.updateCurrentLocationInfo(true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Failed, model.location.value)
            coEvery { location.acquireCurrentLocation() } returns LocationAcquisition.Failure(LocationFailure.UNAVAILABLE)
            model.updateCurrentLocationInfo(true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Unavailable, model.location.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `camera reads branding through domain contract and handles no location`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val provider = mockk<WatermarkConfigProvider>()
            coEvery { provider.getSyLogoImg() } returns "logo-url"
            val location = mockk<LocationFacade>()
            coEvery { location.acquireCurrentLocation() } returns LocationAcquisition.Failure(LocationFailure.UNAVAILABLE)
            val model = CameraViewModel(provider, location, mockk<UnifiedImagePipeline>())
            model.updateSyLogoImg()
            model.updateCurrentLocationInfo()
            advanceUntilIdle()
            assertEquals("logo-url", model.syLogoImg.value)
            assertEquals(CameraLocationState.Unavailable, model.location.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `cancelled branding request does not publish a result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val provider = mockk<WatermarkConfigProvider>()
            coEvery { provider.getSyLogoImg() } throws CancellationException()
            val model = CameraViewModel(provider, mockk(), mockk())
            model.updateSyLogoImg()
            advanceUntilIdle()
            assertEquals("", model.syLogoImg.value)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `capture removes stale coordinates without disabling image processing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val location = mockk<LocationFacade>()
            val sample = LocationResult(30.0, 120.0, "test")
            coEvery { location.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
            every { location.isUsable(sample) } returnsMany listOf(true, false)
            val model = CameraViewModel(mockk(), location, mockk())
            model.updateCurrentLocationInfo(true)
            advanceUntilIdle()
            assertEquals("120.0,30.0", model.coordinatesForCapture())
            assertNull(model.coordinatesForCapture())
            assertEquals(CameraLocationState.Unavailable, model.location.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `new failed request clears old coordinates and no location still allows photo processing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val location = mockk<LocationFacade>()
            val sample = LocationResult(30.123456789, 120.987654321, "test")
            coEvery { location.acquireCurrentLocation() } returnsMany listOf(
                LocationAcquisition.Success(sample), LocationAcquisition.Failure(LocationFailure.TIMEOUT),
            )
            every { location.isUsable(sample) } returns true
            val pipeline = mockk<UnifiedImagePipeline>()
            val request = mockk<com.ytone.longcare.common.image.WatermarkedCaptureRequest>()
            val output = java.io.File("test-photo.jpg")
            coEvery { pipeline.processWatermarkedCapture(request, any()) } returns output
            val model = CameraViewModel(mockk(), location, pipeline)
            model.updateCurrentLocationInfo(true)
            advanceUntilIdle()
            assertNotNull(model.coordinatesForCapture())
            model.updateCurrentLocationInfo(true)
            assertNull(model.coordinatesForCapture())
            advanceUntilIdle()
            assertNull(model.coordinatesForCapture())
            assertEquals(output, model.processCapturedImage(request))
            coVerify(exactly = 1) { pipeline.processWatermarkedCapture(request, any()) }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `leaving camera cancels acquisition and later response cannot restore watermark`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val location = mockk<LocationFacade>()
            var released = false
            coEvery { location.acquireCurrentLocation() } coAnswers {
                try { kotlinx.coroutines.awaitCancellation() } finally { released = true }
            }
            val model = CameraViewModel(mockk(), location, mockk())
            model.updateCurrentLocationInfo(true)
            runCurrent()
            model.stopLocationRequest()
            runCurrent()
            assertTrue(released)
            assertNull(model.coordinatesForCapture())
            assertEquals(CameraLocationState.Unavailable, model.location.value)
        } finally { Dispatchers.resetMain() }
    }

}
