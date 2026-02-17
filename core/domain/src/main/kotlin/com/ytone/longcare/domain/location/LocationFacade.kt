package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.flow.Flow

/**
 * Unified contract for location capabilities:
 * 1. observe location stream
 * 2. fetch single location
 * 3. read cached location
 * 4. manage keep-alive lifecycle
 */
interface LocationFacade {
    fun observeLocations(intervalMs: Long = 30_000L): Flow<LocationResult>

    suspend fun getCurrentLocation(timeoutMs: Long = 10_000L): LocationResult?

    fun getCachedLocation(maxAgeMs: Long = 30_000L): LocationResult?

    fun acquireKeepAlive(owner: String)

    fun releaseKeepAlive(owner: String)
}
