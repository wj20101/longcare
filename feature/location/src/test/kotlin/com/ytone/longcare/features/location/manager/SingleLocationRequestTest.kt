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
        val request = SingleLocationRequest { currentTime }
        var calls = 0
        assertEquals(success, request.acquire { _, _ -> calls++; success })
        assertEquals(LocationAcquisition.Failure(LocationFailure.NETWORK), request.acquire { _, _ ->
            calls++; LocationAcquisition.Failure(LocationFailure.NETWORK)
        })
        assertEquals(2, calls)
    }

    @Test fun `quality retry shares total fifteen second timeout`() = runTest {
        val request = SingleLocationRequest { currentTime }
        var calls = 0
        val result = request.acquire { _, _ ->
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
            val result = SingleLocationRequest { currentTime }.acquire { _, _ -> calls++; LocationAcquisition.Failure(reason) }
            assertEquals(LocationAcquisition.Failure(reason), result)
            assertEquals(if (reason == LocationFailure.QUALITY) 2 else 1, calls)
        }
    }

    @Test fun `conflict returns busy without starting another client cancellation releases slot`() = runTest {
        val request = SingleLocationRequest { currentTime }
        var cleaned = false
        val job = launch {
            request.acquire { _, _ -> try { awaitCancellation() } finally { cleaned = true } }
        }
        runCurrent()
        assertEquals(LocationAcquisition.Failure(LocationFailure.BUSY), request.acquire { _, _ -> error("must not run") })
        job.cancelAndJoin()
        assertTrue(cleaned)
        assertEquals(success, request.acquire { _, _ -> success })
    }

    @Test fun `quality recovers immediately without waiting for position confirmation`() = runTest {
        var calls = 0
        assertEquals(success, SingleLocationRequest { currentTime }.acquire { _, _ ->
            if (++calls == 1) LocationAcquisition.Failure(LocationFailure.QUALITY) else success
        })
        assertEquals(2, calls)
        assertEquals(0, currentTime)
    }

    @Test fun `retry receives remaining budget and does not extend total wait`() = runTest {
        val request = SingleLocationRequest { testScheduler.currentTime }
        val budgets = mutableListOf<Long>()
        val result = request.acquire { remaining, _ ->
            budgets += remaining
            delay(10_000)
            LocationAcquisition.Failure(LocationFailure.QUALITY)
        }
        assertEquals(listOf(15_000L, 5_000L), budgets)
        assertEquals(15_000L, currentTime)
        assertEquals(LocationFailure.TIMEOUT, (result as LocationAcquisition.Failure).reason)
    }

    @Test fun `elapsed deadline rejects success without advancing coroutine time and releases slot`() = runTest {
        var elapsed = 0L
        val request = SingleLocationRequest { elapsed }
        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), request.acquire { _, _ ->
            elapsed = 15_000L
            success
        })
        assertEquals(0L, currentTime)
        assertEquals(success, request.acquire { _, _ -> success })
    }

    @Test fun `retry keeps absolute deadline and late success retains first rejected sample`() = runTest {
        var elapsed = 1_000L
        val request = SingleLocationRequest { elapsed }
        val budgets = mutableListOf<Pair<Long, Long>>()
        val result = request.acquire { remaining, deadline ->
            budgets += remaining to deadline
            if (budgets.size == 1) {
                elapsed += 4_000L
                LocationAcquisition.Failure(LocationFailure.QUALITY, success.location)
            } else {
                elapsed += 11_000L
                success
            }
        }
        assertEquals(listOf(15_000L to 16_000L, 11_000L to 16_000L), budgets)
        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT, success.location), result)
        assertEquals(0L, currentTime)
    }

    @Test fun `explicit terminal failure is not rewritten when cleanup exceeds elapsed deadline`() = runTest {
        var elapsed = 0L
        val terminal = LocationAcquisition.Failure(LocationFailure.PERMISSION)
        val request = SingleLocationRequest { elapsed }
        assertEquals(terminal, request.acquire { _, _ ->
            elapsed = 30_000L
            terminal
        })
    }
}
