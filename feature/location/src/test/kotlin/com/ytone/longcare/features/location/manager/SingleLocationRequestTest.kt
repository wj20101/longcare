package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleLocationRequestTest {
    private val success = LocationAcquisition.Success(LocationResult(30.0, 120.0, "test"))

    @Test fun `new request never reuses a prior success or failure`() = runTest {
        val request = SingleLocationRequest()
        var calls = 0
        assertEquals(success, request.acquire { calls++; success })
        assertEquals(LocationAcquisition.Failure(LocationFailure.NETWORK), request.acquire {
            calls++; LocationAcquisition.Failure(LocationFailure.NETWORK)
        })
        assertEquals(2, calls)
    }

    @Test fun `quality retry shares total fifteen second timeout`() = runTest {
        val request = SingleLocationRequest()
        var calls = 0
        val result = request.acquire {
            calls++
            delay(10_000)
            LocationAcquisition.Failure(LocationFailure.QUALITY)
        }
        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), result)
        assertEquals(15_000, currentTime)
        assertEquals(2, calls)
    }

    @Test fun `at most one quality retry and no retry for configuration or permission`() = runTest {
        for (reason in LocationFailure.entries) {
            var calls = 0
            val result = SingleLocationRequest().acquire { calls++; LocationAcquisition.Failure(reason) }
            assertEquals(LocationAcquisition.Failure(reason), result)
            assertEquals(if (reason == LocationFailure.QUALITY) 2 else 1, calls)
        }
    }

    @Test fun `conflict returns busy without starting another client cancellation releases slot`() = runTest {
        val request = SingleLocationRequest()
        var cleaned = false
        val job = launch {
            request.acquire { try { awaitCancellation() } finally { cleaned = true } }
        }
        runCurrent()
        assertEquals(LocationAcquisition.Failure(LocationFailure.BUSY), request.acquire { error("must not run") })
        job.cancelAndJoin()
        assertTrue(cleaned)
        assertEquals(success, request.acquire { success })
    }

    @Test fun `quality recovers immediately without waiting for position confirmation`() = runTest {
        var calls = 0
        assertEquals(success, SingleLocationRequest().acquire {
            if (++calls == 1) LocationAcquisition.Failure(LocationFailure.QUALITY) else success
        })
        assertEquals(2, calls)
        assertEquals(0, currentTime)
    }
}
