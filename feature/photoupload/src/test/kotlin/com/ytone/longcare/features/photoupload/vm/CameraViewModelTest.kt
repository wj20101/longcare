package com.ytone.longcare.features.photoupload.vm

import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.domain.location.LocationFacade
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
            coEvery { location.getCurrentLocation(any()) } returns LocationResult(latitude = 30.0, longitude = 120.0, provider = "test")
            val model = CameraViewModel(provider, location, mockk())
            refreshCameraOnResume(model, fineLocationGranted = true, coarseLocationGranted = true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Coordinates("120.0,30.0"), model.location.value)
            refreshCameraOnResume(model, fineLocationGranted = false, coarseLocationGranted = true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Coordinates("120.0,30.0"), model.location.value)
            refreshCameraOnResume(model, fineLocationGranted = false, coarseLocationGranted = false)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Unavailable, model.location.value)
            coVerify(exactly = 2) { location.getCurrentLocation(any()) }
            refreshCameraOnResume(model, fineLocationGranted = false, coarseLocationGranted = true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Coordinates("120.0,30.0"), model.location.value)
            coVerify(exactly = 3) { location.getCurrentLocation(any()) }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `revoking permission ignores a late noncancellable location response`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val location = mockk<LocationFacade>()
            coEvery { location.getCurrentLocation(any()) } coAnswers {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    kotlinx.coroutines.delay(100)
                    LocationResult(latitude = 30.0, longitude = 120.0, provider = "test")
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
            coEvery { location.getCurrentLocation(any()) } throws IllegalStateException("disabled")
            val model = CameraViewModel(mockk(), location, mockk())
            model.updateCurrentLocationInfo(true)
            advanceUntilIdle()
            assertEquals(CameraLocationState.Failed, model.location.value)
            coEvery { location.getCurrentLocation(any()) } returns null
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
            coEvery { location.getCurrentLocation(any()) } returns null
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
}
