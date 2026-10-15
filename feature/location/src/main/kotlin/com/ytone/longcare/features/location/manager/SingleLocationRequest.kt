package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** 一个在途槽、最多两次采集，重试共用同一协程单调时钟预算，不排队。 */
internal class SingleLocationRequest(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val inFlight = Mutex()

    suspend fun acquire(
        retryIf: (LocationAcquisition.Failure) -> Boolean = { it.reason == LocationFailure.QUALITY },
        request: suspend (remainingMs: Long) -> LocationAcquisition,
    ): LocationAcquisition {
        if (!inFlight.tryLock()) return LocationAcquisition.Failure(LocationFailure.BUSY)
        try {
            val started = nowMillis()
            var evidence: LocationAcquisition.Failure? = null
            return withTimeoutOrNull(TIMEOUT_MS) {
                val first = request(TIMEOUT_MS)
                if (first !is LocationAcquisition.Failure) return@withTimeoutOrNull first
                evidence = first
                val remaining = TIMEOUT_MS - (nowMillis() - started).coerceAtLeast(0L)
                if (!retryIf(first) || remaining < MIN_ATTEMPT_MS) return@withTimeoutOrNull first
                val second = request(remaining)
                if (second is LocationAcquisition.Failure) {
                    // 第二次无样本时保留第一次拒绝原因的证据，绝不降级成 Success。
                    second.copy(location = second.location ?: first.location)
                } else second
            } ?: LocationAcquisition.Failure(
                LocationFailure.TIMEOUT,
                evidence?.location,
            )
        } finally {
            inFlight.unlock()
        }
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
        private const val MIN_ATTEMPT_MS = 1_000L
    }
}
