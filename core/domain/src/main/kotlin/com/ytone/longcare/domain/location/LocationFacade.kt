package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/**
 * Unified contract for location capabilities:
 * 1. fetch single location
 * 2. read cached location
 * 3. manage keep-alive lifecycle
 *
 * Continuous samples are intentionally not exposed here. The foreground location
 * service is their sole SDK collector and publishes them through LocationSampleStore.
 */
interface LocationFacade {
    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_FAST_LOCATION_TIMEOUT_MS): LocationResult?

    suspend fun getFreshLocation(timeoutMs: Long = DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult?

    fun getCachedLocation(
        maxAgeMs: Long = DEFAULT_LOCATION_CACHE_MAX_AGE_MS,
    ): LocationResult?

    fun acquireKeepAlive(owner: String)

    fun releaseKeepAlive(owner: String)

    /** 定位权限授予后调用，重启定位引擎 */
    fun notifyPermissionGranted() {}

    companion object {
        const val MIN_FAST_LOCATION_TIMEOUT_MS: Long = 1_000L
        const val DEFAULT_FAST_LOCATION_TIMEOUT_MS: Long = 4_000L
        const val MAX_FAST_LOCATION_TIMEOUT_MS: Long = DEFAULT_FAST_LOCATION_TIMEOUT_MS

        const val MIN_FRESH_LOCATION_TIMEOUT_MS: Long = 8_000L
        const val DEFAULT_FRESH_LOCATION_TIMEOUT_MS: Long = 10_000L
        const val MAX_FRESH_LOCATION_TIMEOUT_MS: Long = 15_000L

        const val DEFAULT_LOCATION_CACHE_MAX_AGE_MS: Long = 30_000L
        const val BUSINESS_LOCATION_CACHE_MAX_AGE_MS: Long = 5 * 60 * 1000L
    }
}
