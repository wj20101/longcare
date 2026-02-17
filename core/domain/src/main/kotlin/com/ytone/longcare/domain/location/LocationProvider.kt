package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/**
 * Infrastructure-level location source contract.
 */
interface LocationProvider {
    /**
     * Get current location once.
     */
    suspend fun getCurrentLocation(): LocationResult?

    /**
     * Release provider resources.
     */
    fun destroy()
}
