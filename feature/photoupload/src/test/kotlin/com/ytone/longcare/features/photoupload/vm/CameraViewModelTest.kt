package com.ytone.longcare.features.photoupload.vm

import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.WatermarkConfigProvider
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.UserSessionSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    private val main = StandardTestDispatcher()
    private val facade = mockk<LocationFacade>()
    private val sample = LocationResult(30.123456789, 120.987654321, "test")
    private val session = MutableStateFlow(UserSessionSnapshot(1, mockk()))
    private val branding = mockk<WatermarkConfigProvider>()
    private lateinit var camera: CameraViewModel

    @Before fun setup() {
        Dispatchers.setMain(main)
        coEvery { branding.getSyLogoImg() } returns "logo"
        val sessions = mockk<UserSessionRepository>()
        every { sessions.sessionSnapshot } returns session
        camera = CameraViewModel(branding, facade, mockk(), sessions)
    }

    private suspend fun shutterStamp(onFailure: (LocationFailure) -> Unit) =
        camera.shutterStamp(prepareWatermark = { true }, onFailure = onFailure)

    @After fun tearDown() {
        camera.onCameraPause()
        Dispatchers.resetMain()
    }

    @Test fun shutterWaitsForPrefetchAndReusesFreshSample() = runTest(main) {
        val result = CompletableDeferred<LocationAcquisition>()
        coEvery { facade.acquireCurrentLocation() } coAnswers { result.await() }
        every { facade.isUsable(sample) } returns true
        camera.onCameraResume(true)
        val captures = mutableListOf<CaptureStamp>()
        var prepared = 0
        val waiting = async {
            val stamp = camera.shutterStamp({
                prepared++
                true
            }) { fail(it.name) }
            captures.add(requireNotNull(stamp))
        }
        runCurrent()
        assertTrue(captures.isEmpty())
        assertEquals(0, prepared)
        result.complete(LocationAcquisition.Success(sample))
        waiting.await()
        assertEquals(1, prepared)
        captures.add(requireNotNull(shutterStamp { fail(it.name) }))
        assertEquals(listOf(sample, sample), captures.map { it.location })
        assertTrue(captures.all { it.time.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")) })
        assertEquals("logo", camera.syLogoImg.value)
        coVerify(exactly = 1) { facade.acquireCurrentLocation() }
    }

    @Test fun expiredSampleTriggersOneRefreshAndUsesOnlyNewCoordinates() = runTest(main) {
        val other = sample.copy(longitude = 121.1)
        coEvery { facade.acquireCurrentLocation() } returnsMany listOf(
            LocationAcquisition.Success(sample), LocationAcquisition.Success(other),
        )
        every { facade.isUsable(sample) } returns false
        every { facade.isUsable(other) } returns true
        camera.onCameraResume(true)
        runCurrent()
        assertEquals(other, shutterStamp { fail(it.name) }?.location)
        coVerify(exactly = 2) { facade.acquireCurrentLocation() }
    }

    @Test fun fixExpiringWhileDrawingUsesNewLocationAndNewLogo() = runTest(main) {
        val fresh = sample.copy(longitude = 121.1)
        var previewLogo = "first"
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        every { facade.isUsable(sample) } returnsMany listOf(true, false)
        every { facade.isUsable(fresh) } returns true
        camera.onCameraResume(true)
        runCurrent()
        coEvery { facade.acquireCurrentLocation() } coAnswers {
            previewLogo = "updated"
            LocationAcquisition.Success(fresh)
        }
        val snapshots = mutableListOf<String>()
        val stamp = camera.shutterStamp({
            snapshots.add(previewLogo)
        }) { fail(it.name) }
        assertEquals(listOf("first", "updated"), snapshots)
        assertEquals(fresh, stamp?.location)
        coVerify(exactly = 2) { facade.acquireCurrentLocation() }
    }

    @Test fun everyLocationFailureBlocksCaptureAndUserCanRetry() = runTest(main) {
        camera.onCameraResume(false)
        assertEquals(CameraLocationState.Unavailable, camera.location.value)
        for (failure in LocationFailure.entries) {
            coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Failure(failure)
            var reported: LocationFailure? = null
            assertNull(shutterStamp { reported = it })
            assertEquals(failure, reported)
        }
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        every { facade.isUsable(sample) } returns true
        assertEquals(sample, shutterStamp { fail(it.name) }?.location)
    }

    @Test fun rejectedFreshResultCannotDispatchShutter() = runTest(main) {
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        every { facade.isUsable(sample) } returns false
        camera.onCameraResume(true)
        var reason: LocationFailure? = null
        assertNull(shutterStamp { reason = it })
        assertEquals(LocationFailure.QUALITY, reason)
        coVerify(exactly = 1) { facade.acquireCurrentLocation() }
    }

    @Test fun permissionRevocationAndExceptionDoNotReuseAnOlderSample() = runTest(main) {
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        every { facade.isUsable(sample) } returns true
        camera.onCameraResume(true)
        runCurrent()
        assertEquals(CameraLocationState.Coordinates(sample), camera.location.value)
        camera.onCameraResume(false)
        assertEquals(CameraLocationState.Unavailable, camera.location.value)
        coEvery { facade.acquireCurrentLocation() } throws IllegalStateException("unavailable")
        camera.onCameraResume(true)
        runCurrent()
        assertEquals(CameraLocationState.Failed, camera.location.value)
        var failure: LocationFailure? = null
        assertNull(shutterStamp { failure = it })
        assertEquals(LocationFailure.UNAVAILABLE, failure)
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        assertEquals(sample, shutterStamp { fail(it.name) }?.location)
    }

    @Test fun cancellationWhileReadingBrandingLeavesLogoEmpty() = runTest(main) {
        coEvery { branding.getSyLogoImg() } throws CancellationException()
        camera.onCameraResume(false)
        runCurrent()
        assertEquals("", camera.syLogoImg.value)
    }

    @Test fun countdownRechecksAtTheEndAndBlocksAStaleFix() = runTest(main) {
        coEvery { facade.acquireCurrentLocation() } returnsMany listOf(
            LocationAcquisition.Success(sample), LocationAcquisition.Failure(LocationFailure.TIMEOUT),
        )
        every { facade.isUsable(sample) } returns false
        camera.onCameraResume(true)
        val ticks = mutableListOf<Int>()
        var reason: LocationFailure? = null
        val job = async {
            camera.awaitCountdown(3) { ticks += it }
            assertNull(shutterStamp { reason = it })
        }
        advanceTimeBy(2999)
        assertNull(reason)
        coVerify(exactly = 1) { facade.acquireCurrentLocation() }
        job.await()
        assertEquals(listOf(3, 2, 1, 0), ticks)
        assertEquals(LocationFailure.TIMEOUT, reason)
    }

    @Test fun pauseInTheLastSecondCancelsTheShutter() = runTest(main) {
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        camera.onCameraResume(true)
        val shutter = async {
            camera.awaitCountdown(3) {}
            shutterStamp { fail(it.name) }
        }
        advanceTimeBy(2900)
        shutter.cancel()
        camera.onCameraPause()
        camera.onCameraResume(false)
        assertTrue(runCatching { shutter.await() }.exceptionOrNull() is CancellationException)
        coVerify(exactly = 1) { facade.acquireCurrentLocation() }
    }

    @Test fun pausedCameraIgnoresLateResultEvenAfterResume() = runTest(main) {
        coEvery { facade.acquireCurrentLocation() } coAnswers {
            withContext(NonCancellable) { delay(100); LocationAcquisition.Success(sample) }
        }
        camera.onCameraResume(true)
        val pending = async { shutterStamp { fail(it.name) } }
        runCurrent()
        camera.onCameraPause()
        camera.onCameraResume(false)
        assertTrue(runCatching { pending.await() }.exceptionOrNull() is CancellationException)
        assertEquals(CameraLocationState.Unavailable, camera.location.value)
    }

    @Test fun switchingAccountInvalidatesCachedCapture() = runTest(main) {
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        camera.onCameraResume(true)
        runCurrent()
        session.value = UserSessionSnapshot(2, mockk())
        val result = runCatching { shutterStamp { fail(it.name) } }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
