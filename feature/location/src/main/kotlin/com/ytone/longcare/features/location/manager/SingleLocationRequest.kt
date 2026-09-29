package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** 一个在途槽、最多两次采集，重试共用同一协程单调时钟预算，不排队。 */
internal class SingleLocationRequest {
    private val inFlight = Mutex()

    suspend fun acquire(request: suspend () -> LocationAcquisition): LocationAcquisition {
        if (!inFlight.tryLock()) return LocationAcquisition.Failure(LocationFailure.BUSY)
        try {
            return withTimeoutOrNull(TIMEOUT_MS) {
                val first = request()
                if (first == LocationAcquisition.Failure(LocationFailure.QUALITY)) request() else first
            } ?: LocationAcquisition.Failure(LocationFailure.TIMEOUT)
        } finally {
            inFlight.unlock()
        }
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
