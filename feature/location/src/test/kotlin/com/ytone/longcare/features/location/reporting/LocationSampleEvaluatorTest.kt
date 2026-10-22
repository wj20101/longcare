package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.model.LocationResult
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocationSampleEvaluatorTest {
    private var elapsed = 1_000L
    private var now = 100_000L
    private val clock = mockk<LocationClock>()

    @Before fun setup() {
        every { clock.elapsedRealtime() } answers { elapsed }
        every { clock.currentTimeMillis() } answers { now }
    }

    @After fun cleanup() = unmockkAll()

    @Test fun `quality evaluation does not impose a second reporting interval`() {
        val evaluator = LocationSampleEvaluator(elapsed, clock)
        assertNull(evaluator.rejection(sample()))
        evaluator.onUploadStarted(sample())
        now++; elapsed++
        assertNull(evaluator.rejection(sample()))
        evaluator.onUploadStarted(sample())
        assertNotNull("The exact same sample must not upload twice", evaluator.rejection(sample()))
    }

    @Test fun `rejected precision does not consume the next eligible sample and restart rejects old point`() {
        val evaluator = LocationSampleEvaluator(elapsed, clock)
        assertNotNull(evaluator.rejection(sample().copy(accuracy = 1_000f)))
        assertNull(evaluator.rejection(sample()))
        val next = LocationSampleEvaluator(elapsed + 1, clock)
        assertNotNull(next.rejection(sample()))
    }

    @Test fun `candidate checks do not consume the sample until actual dispatch`() {
        val evaluator = LocationSampleEvaluator(elapsed, clock)
        val candidate = sample()
        repeat(3) { assertNull(evaluator.rejection(candidate)) }
        evaluator.onUploadStarted(candidate)
        assertNotNull(evaluator.rejection(candidate))
    }

    @Test fun `valid candidate is rechecked after waiting beyond the age limit`() {
        val evaluator = LocationSampleEvaluator(elapsed, clock)
        val candidate = sample()
        assertNull(evaluator.rejection(candidate))
        now += 60_001L
        elapsed += 60_001L
        assertNotNull(evaluator.rejection(candidate))
        assertNull(evaluator.rejection(sample()))
    }

    @Test fun `refreshed wall time cannot resend the same or older consumed source tick`() {
        val evaluator = LocationSampleEvaluator((elapsed - 1_000L).coerceAtLeast(0L), clock)
        val first = sample().copy(locationType = 2, sourceElapsedRealtime = elapsed)
        repeat(2) { assertNull(evaluator.rejection(first)) }
        evaluator.onUploadStarted(first)

        now += 1_000L
        elapsed += 1_000L
        val refreshed = sample().copy(locationType = 2, sourceElapsedRealtime = first.sourceElapsedRealtime)
        assertEquals(LocationQuality.Rejection.ORDER, evaluator.rejection(refreshed))
        assertEquals(LocationQuality.Rejection.ORDER,
            evaluator.rejection(refreshed.copy(sourceElapsedRealtime = first.sourceElapsedRealtime - 1L)))
        assertNull(evaluator.rejection(refreshed.copy(sourceElapsedRealtime = elapsed)))
        // A different reporting owner has its own consumed-source watermark.
        assertNull(LocationSampleEvaluator((elapsed - 2_000L).coerceAtLeast(0L), clock).rejection(refreshed))
    }

    @Test fun `unknown source tick remains compatible without clearing a positive consumed watermark`() {
        val evaluator = LocationSampleEvaluator((elapsed - 1_000L).coerceAtLeast(0L), clock)
        val sourceTick = elapsed
        val first = sample().copy(sourceElapsedRealtime = sourceTick)
        assertNull(evaluator.rejection(first))
        evaluator.onUploadStarted(first)

        now++; elapsed++
        val unknown = sample()
        assertNull(evaluator.rejection(unknown))
        evaluator.onUploadStarted(unknown)

        now++; elapsed++
        assertEquals(LocationQuality.Rejection.ORDER,
            evaluator.rejection(sample().copy(sourceElapsedRealtime = sourceTick)))
        assertNull(evaluator.rejection(sample().copy(sourceElapsedRealtime = elapsed)))
    }

    @Test fun `original quality rejection takes priority over a repeated source tick`() {
        val evaluator = LocationSampleEvaluator((elapsed - 1_000L).coerceAtLeast(0L), clock)
        val first = sample().copy(sourceElapsedRealtime = elapsed)
        evaluator.onUploadStarted(first)
        now++; elapsed++
        assertEquals(LocationQuality.Rejection.ACCURACY, evaluator.rejection(
            sample().copy(sourceElapsedRealtime = first.sourceElapsedRealtime, accuracy = 1_000f),
        ))
    }

    @Test fun `reporting uses one monotonic watermark across GPS clock offsets and phone clock changes`() {
        now = 1_800_000_000_000L
        val evaluator = LocationSampleEvaluator(elapsed, clock)
        val first = sample().copy(locationType = 1, locationTime = now + 5_000, sourceElapsedRealtime = elapsed)
        assertNull(evaluator.rejection(first))
        evaluator.onUploadStarted(first)
        now -= 60_000
        elapsed += 30_000
        val next = sample().copy(locationType = 1, locationTime = now - 60_000, sourceElapsedRealtime = elapsed)
        assertNull(evaluator.rejection(next))
        evaluator.onUploadStarted(next)
        assertEquals(LocationQuality.Rejection.ORDER, evaluator.rejection(next.copy(locationTime = now + 5_000)))
        assertEquals(LocationQuality.Rejection.ORDER, evaluator.rejection(next.copy(sourceElapsedRealtime = first.sourceElapsedRealtime)))
    }

    @Test fun `monotonic session boundary rejects old source after wall clock jumps`() {
        val evaluator = LocationSampleEvaluator(elapsed, clock)
        now += 60_000
        elapsed += 30_000
        val old = sample().copy(sourceElapsedRealtime = 999, locationTime = now + 5_000)
        assertEquals(LocationQuality.Rejection.ORDER, evaluator.rejection(old))
        assertNull(evaluator.rejection(old.copy(sourceElapsedRealtime = elapsed)))
    }

    private fun sample() = LocationResult(30.0, 120.0, "network", 50f, "GCJ02", 5, 2,
        locationTime = now, receivedAt = now, receivedElapsedRealtime = elapsed)
}
