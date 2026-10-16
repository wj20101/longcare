package com.ytone.longcare.features.location.service

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.features.location.reporting.LocationClock
import com.ytone.longcare.features.location.reporting.LocationReportingSession
import com.ytone.longcare.features.location.reporting.LocationSampleEvaluator
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.result.ApiResult
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLocationSessionTest {
    private val account = LocationAccount(1, 2, 3)
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
        val gate = LocationReportingSession(1, "owner", account)
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
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
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
            if (it < 3) advanceBy(30_000)
        }
        job.cancel()
    }

    @Test
    fun `stop cancels in flight upload before Android onDestroy and blocks late samples`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
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
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
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
    fun `order ending during sample validation prevents HTTP dispatch`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var uploads = 0
        every { clock.elapsedRealtime() } answers {
            gate.invalidate()
            elapsed
        }
        val job = backgroundScope.launch(start = CoroutineStart.LAZY) {
            executor(gate, samples) { uploads++; ApiResult.Success(Unit) }.run()
        }
        gate.attach(job)
        job.start()
        samples.send(sample())
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(0, uploads)
    }

    @Test
    fun `late upload response from ended order is ignored while the next order uploads normally`() = runTest {
        val previous = LocationReportingSession(1, "old-owner", account).apply { setEnabled(true) }
        val previousSamples = Channel<LocationResult>(Channel.UNLIMITED)
        val finishPrevious = CompletableDeferred<Unit>()
        val previousJob = backgroundScope.launch(start = CoroutineStart.LAZY) {
            executor(previous, previousSamples) {
                withContext(NonCancellable) { finishPrevious.await() }
                ApiResult.Failure(400, "late result")
            }.run()
        }
        previous.attach(previousJob)
        previousJob.start()
        previousSamples.send(sample())
        runCurrent()
        previous.invalidate()

        val next = LocationReportingSession(2, "new-owner", account).apply { setEnabled(true) }
        val nextSamples = Channel<LocationResult>(Channel.UNLIMITED)
        var nextUploads = 0
        val nextJob = backgroundScope.launch {
            executor(next, nextSamples) { nextUploads++; ApiResult.Success(Unit) }.run()
        }
        nextSamples.send(sample())
        runCurrent()
        finishPrevious.complete(Unit)
        runCurrent()
        assertEquals(1, nextUploads)
        assertTrue(next.canUpload())
        verify(exactly = 0) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR, any(), any())
        }
        nextJob.cancel()
    }

    @Test
    fun `one current candidate replaces action bursts without queueing or parallel uploads`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
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
        assertEquals(listOf(31.0), uploaded)
        samples.send(sample(31.4))
        runCurrent()
        assertEquals(listOf(31.0), uploaded)
        advanceBy(30_000)
        assertEquals(listOf(31.0, 31.4), uploaded)
        advanceBy(30_000)
        assertEquals("A consumed candidate must never be replayed", listOf(31.0, 31.4), uploaded)
        job.cancel()
    }

    @Test
    fun `stalled upload is cancelled within one reporting period and fresh samples continue`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var attempts = 0
        var cancelled = false
        val results = mutableListOf<ApiResult<Unit>>()
        val job = backgroundScope.launch {
            executor(gate, samples, onResult = { results += it }) {
                attempts++
                if (attempts == 1) {
                    try { awaitCancellation() } finally { cancelled = true }
                }
                ApiResult.Success(Unit)
            }.run()
        }
        samples.send(sample())
        runCurrent()
        // The next callback may precede timeout by 1 ms. It stays the current candidate,
        // then uploads at the same period boundary after the timed-out request is joined.
        advanceBy(29_999)
        samples.send(sample())
        runCurrent()
        advanceBy(1)
        assertTrue("A stalled upload must not silently occupy all following callbacks", cancelled)
        assertTrue(gate.canUpload())
        assertEquals(2, attempts)
        assertTrue(results.first() is ApiResult.Exception)
        assertTrue(results.last() is ApiResult.Success)
        job.cancel()
    }

    @Test
    fun `invalid and pre session locations never upload`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var uploads = 0
        val job = backgroundScope.launch {
            ServiceLocationSession(
                gate, samples.receiveAsFlow(), { uploads++; ApiResult.Success(Unit) },
                LocationSampleEvaluator(1, now + 10, clock),
            ).run()
        }
        samples.send(sample(Double.NaN))
        runCurrent()
        samples.send(sample())
        runCurrent()
        assertEquals(0, uploads)
        job.cancel()
    }

    @Test
    fun `sample waiting behind slow upload is rechecked and never uploaded after expiry`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        val finishFirst = CompletableDeferred<Unit>()
        var uploads = 0
        val job = backgroundScope.launch {
            executor(gate, samples) {
                uploads++
                if (uploads == 1) finishFirst.await()
                ApiResult.Success(Unit)
            }.run()
        }
        samples.send(sample())
        runCurrent()
        samples.send(sample())
        runCurrent()
        now += 61_000
        elapsed += 61_000
        finishFirst.complete(Unit)
        advanceBy(30_000)
        assertEquals(1, uploads)
        samples.send(sample())
        runCurrent()
        assertEquals(2, uploads)
        job.cancel()
    }

    @Test
    fun `reporting drops low precision samples and recovers without ending the order`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        val uploaded = mutableListOf<Float>()
        val job = backgroundScope.launch {
            executor(gate, samples) { uploaded += it.accuracy; ApiResult.Success(Unit) }.run()
        }
        listOf(2_000f, 201f, 200f, 100f).forEach {
            samples.send(sample().copy(accuracy = it))
            runCurrent()
        }
        assertEquals(listOf(200f), uploaded)
        advanceBy(30_000)
        assertEquals(listOf(200f, 100f), uploaded)
        assertTrue(gate.canUpload())
        job.cancel()
    }

    @Test
    fun `SDK callbacks just before each period are sampled without losing every other period`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var uploads = 0
        val job = backgroundScope.launch {
            executor(gate, samples) { uploads++; ApiResult.Success(Unit) }.run()
        }
        samples.send(sample())
        runCurrent()
        assertEquals(1, uploads)
        repeat(4) { period ->
            advanceBy(29_999)
            samples.send(sample())
            runCurrent()
            assertEquals(period + 1, uploads)
            advanceBy(1)
            assertEquals(period + 2, uploads)
        }
        job.cancel()
    }

    @Test
    fun `cancellation releases the sole upstream subscription and in flight HTTP`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        var subscriptions = 0
        var releases = 0
        var uploadCancelled = false
        val source = flow {
            subscriptions++
            try {
                emit(sample())
                awaitCancellation()
            } finally { releases++ }
        }
        val job = backgroundScope.launch {
            ServiceLocationSession(gate, source, {
                try { awaitCancellation() } finally { uploadCancelled = true }
            }, LocationSampleEvaluator(1, 0, clock)).run()
        }
        runCurrent()
        assertEquals(1, subscriptions)
        job.cancel()
        runCurrent()
        assertEquals(1, releases)
        assertTrue(uploadCancelled)
    }

    @Test
    fun `unexpected upload failure does not kill SDK subscription or next reporting period`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        val samples = Channel<LocationResult>(Channel.UNLIMITED)
        var attempts = 0
        val job = backgroundScope.launch {
            executor(gate, samples) {
                attempts++
                if (attempts == 1) error("unexpected adapter failure")
                ApiResult.Success(Unit)
            }.run()
        }
        samples.send(sample())
        runCurrent()
        assertTrue(job.isActive)
        samples.send(sample())
        advanceBy(30_000)
        assertEquals(2, attempts)
        assertTrue(job.isActive)
        job.cancel()
    }

    @Test
    fun `finite source completes with one subscription and release without replaying last sample`() = runTest {
        val gate = LocationReportingSession(1, "owner", account).apply { setEnabled(true) }
        var subscriptions = 0
        var releases = 0
        var uploads = 0
        val source = flow {
            subscriptions++
            try { emit(sample()) } finally { releases++ }
        }
        val job = backgroundScope.launch {
            ServiceLocationSession(gate, source, { uploads++; ApiResult.Success(Unit) },
                LocationSampleEvaluator(1, 0, clock)).run()
        }
        runCurrent()
        assertEquals(1, subscriptions)
        assertEquals(1, uploads)
        assertEquals(1, releases)
        advanceBy(30_000)
        assertTrue(job.isCompleted)
        assertEquals(1, uploads)
        assertEquals(1, releases)
    }

    private fun executor(
        gate: LocationReportingSession,
        samples: Channel<LocationResult>,
        onResult: (ApiResult<Unit>) -> Unit = {},
        upload: suspend (LocationResult) -> ApiResult<Unit>,
    ) = ServiceLocationSession(gate, samples.receiveAsFlow(), upload,
        LocationSampleEvaluator(1, 0, clock), onUploadResult = onResult)

    private var now = 100_000L
    private var elapsed = 1_000L
    private val clock = mockk<LocationClock> {
        every { currentTimeMillis() } answers { now }
        every { elapsedRealtime() } answers { elapsed }
    }

    private fun TestScope.advanceBy(millis: Long) {
        now += millis
        elapsed += millis
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun sample(latitude: Double = 31.23): LocationResult {
        now++
        elapsed++
        return LocationResult(
            latitude = latitude, longitude = 121.47, provider = "amap_continuous",
            accuracy = 8f, coordType = "GCJ02", locationType = 5, trustedLevel = 1,
            locationTime = now, receivedAt = now, receivedElapsedRealtime = elapsed,
        )
    }
}
