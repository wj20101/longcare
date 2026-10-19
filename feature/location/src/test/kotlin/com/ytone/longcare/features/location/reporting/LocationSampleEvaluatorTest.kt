package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.features.location.tracker.LocationEventTracker
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
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackEvent(any(), any()) } just Runs
        every { LocationEventTracker.trackLocationSample(any(), any(), any(), any()) } just Runs
    }

    @After fun cleanup() = unmockkAll()

    @Test fun `quality evaluation does not impose a second reporting interval`() {
        val evaluator = LocationSampleEvaluator(1, now, clock)
        assertTrue(evaluator.isEligible(sample()))
        evaluator.onUploadStarted(sample())
        now++; elapsed++
        assertTrue(evaluator.isEligible(sample()))
        evaluator.onUploadStarted(sample())
        assertFalse("The exact same sample must not upload twice", evaluator.isEligible(sample()))
    }

    @Test fun `rejected precision does not consume the next eligible sample and restart rejects old point`() {
        val evaluator = LocationSampleEvaluator(1, now, clock)
        assertFalse(evaluator.isEligible(sample().copy(accuracy = 1_000f)))
        assertTrue(evaluator.isEligible(sample()))
        val next = LocationSampleEvaluator(2, now + 1, clock)
        assertFalse(next.isEligible(sample()))
    }

    @Test fun `candidate checks do not consume the sample until actual dispatch`() {
        val evaluator = LocationSampleEvaluator(1, now, clock)
        val candidate = sample()
        repeat(3) { assertTrue(evaluator.isEligible(candidate)) }
        evaluator.onUploadStarted(candidate)
        assertFalse(evaluator.isEligible(candidate))
    }

    @Test fun `valid candidate is rechecked after waiting beyond the age limit`() {
        val evaluator = LocationSampleEvaluator(1, now, clock)
        val candidate = sample()
        assertTrue(evaluator.isEligible(candidate))
        now += 60_001L
        elapsed += 60_001L
        assertFalse(evaluator.isEligible(candidate))
        assertTrue(evaluator.isEligible(sample()))
    }

    private fun sample() = LocationResult(30.0, 120.0, "network", 50f, "GCJ02", 5, 2,
        locationTime = now, receivedAt = now, receivedElapsedRealtime = elapsed)
}
