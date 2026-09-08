package com.ytone.longcare.features.location.service

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.location.reporting.LocationClock
import com.ytone.longcare.features.location.reporting.LocationReportingSession
import com.ytone.longcare.features.location.reporting.LocationSampleEvaluator
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.result.ApiResult
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLocationSessionTest {
    @Before
    fun setup() {
        KLogger.updateConfig { enabled = false }
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackError(any(), any(), any()) } just runs
        every { LocationEventTracker.trackLocationSample(any(), any(), any(), any()) } just runs
        every { LocationEventTracker.trackEvent(any(), any()) } just runs
    }

    @After
    fun cleanup() = unmockkAll()

    @Test
    fun `inactive gate never uploads`() = runTest {
        val gate = LocationReportingSession(1, "owner")
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var uploads = 0
        val job = backgroundScope.launch { executor(gate, samples) { uploads++; ApiResult.Success(Unit) }.run() }
        samples.send(sample())
        runCurrent()
        assertEquals(0, uploads)
        job.cancel()
    }

    @Test
    fun `repeated business and network failures do not change business state or retry a point`() = runTest {
        val gate = LocationReportingSession(1, "owner").apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var uploads = 0
        val job = backgroundScope.launch {
            executor(gate, samples) {
                uploads++
                when (uploads) {
                    1, 2 -> ApiResult.Failure(400, "business error")
                    3 -> ApiResult.Exception(IllegalStateException("offline"))
                    else -> ApiResult.Success(Unit)
                }
            }.run()
        }
        repeat(4) {
            samples.send(sample())
            runCurrent()
            assertEquals(it + 1, uploads)
            assertTrue(gate.canUpload())
        }
        job.cancel()
    }

    @Test
    fun `stop cancels in flight upload before Android onDestroy and blocks late samples`() = runTest {
        val gate = LocationReportingSession(1, "owner").apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var uploads = 0
        var cancelled = false
        val job = backgroundScope.launch(start = CoroutineStart.LAZY) {
            executor(gate, samples) {
                uploads++
                try { awaitCancellation() } finally { cancelled = true }
            }.run()
        }
        gate.attach(job)
        job.start()
        samples.send(sample())
        runCurrent()
        gate.invalidate()
        assertTrue(job.isCancelled)
        assertFalse(gate.canUpload())
        samples.send(sample())
        runCurrent()
        assertTrue(cancelled)
        assertEquals(1, uploads)
    }

    @Test
    fun `stop before lazy collector attachment prevents it from starting`() = runTest {
        val gate = LocationReportingSession(1, "owner").apply { setEnabled(true) }
        var ran = false
        val job = backgroundScope.launch(start = CoroutineStart.LAZY) { ran = true }
        gate.invalidate()
        gate.attach(job)
        job.start()
        runCurrent()
        assertFalse(ran)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `slow upload retains only latest pending sample`() = runTest {
        val gate = LocationReportingSession(1, "owner").apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        val finishFirst = CompletableDeferred<Unit>()
        val uploaded = mutableListOf<Double>()
        val job = backgroundScope.launch {
            executor(gate, samples) {
                uploaded += it.latitude
                if (uploaded.size == 1) finishFirst.await()
                ApiResult.Success(Unit)
            }.run()
        }
        samples.send(sample(31.0))
        runCurrent()
        samples.send(sample(31.1))
        runCurrent()
        samples.send(sample(31.2))
        runCurrent()
        samples.send(sample(31.3))
        runCurrent()
        finishFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf(31.0, 31.3), uploaded)
        job.cancel()
    }

    @Test
    fun `invalid and pre session locations never upload`() = runTest {
        val gate = LocationReportingSession(1, "owner").apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var uploads = 0
        val job = backgroundScope.launch {
            ServiceLocationSession(
                gate, samples.receiveAsFlow(), { uploads++; ApiResult.Success(Unit) },
                LocationSampleEvaluator(1, System.currentTimeMillis(), LocationClock()),
            ).run()
        }
        samples.send(sample(Double.NaN))
        runCurrent()
        samples.send(sample().copy(locationTime = System.currentTimeMillis() - 30_000))
        runCurrent()
        assertEquals(0, uploads)
        job.cancel()
    }

    private fun executor(
        gate: LocationReportingSession,
        samples: Channel<LocationResult>,
        upload: suspend (LocationResult) -> ApiResult<Unit>,
    ) = ServiceLocationSession(gate, samples.receiveAsFlow(), upload, LocationSampleEvaluator(1, 0, LocationClock()))

    private fun sample(latitude: Double = 31.23) = LocationResult(
        latitude = latitude, longitude = 121.47, provider = "amap_continuous",
        accuracy = 8f, coordType = "GCJ02", locationType = 5, trustedLevel = 2, locationTime = 0,
    )
}
