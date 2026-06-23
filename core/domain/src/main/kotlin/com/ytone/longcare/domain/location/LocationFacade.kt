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

    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_FAST_LOCATION_TIMEOUT_MS): LocationResult?

    suspend fun getFreshLocation(timeoutMs: Long = DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult?

    fun getCachedLocation(maxAgeMs: Long = 30_000L): LocationResult?

    fun acquireKeepAlive(owner: String)

    fun releaseKeepAlive(owner: String)

    /** 定位权限授予后调用，重启定位引擎 */
    fun notifyPermissionGranted() {}

    companion object {
        const val DEFAULT_FAST_LOCATION_TIMEOUT_MS: Long = 4_000L
        const val DEFAULT_FRESH_LOCATION_TIMEOUT_MS: Long = 10_000L
        const val BUSINESS_LOCATION_CACHE_MAX_AGE_MS: Long = 5 * 60 * 1000L
    }
}
