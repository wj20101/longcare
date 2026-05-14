package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class DefaultLocationFacade @Inject constructor(
    private val continuousAmapLocationManager: ContinuousAmapLocationManager,
    private val locationStateManager: LocationStateManager,
    private val locationKeepAliveManager: LocationKeepAliveManager
) : LocationFacade {

    override fun observeLocations(intervalMs: Long): Flow<LocationResult> {
        return continuousAmapLocationManager.startContinuousLocation(intervalMs)
    }

    override suspend fun getCurrentLocation(timeoutMs: Long): LocationResult? {
        locationStateManager.getValidLocation(LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS)?.let { return it }

        val boundedTimeoutMs = timeoutMs.coerceIn(MIN_LOCATION_TIMEOUT_MS, MAX_LOCATION_TIMEOUT_MS)

        val amapResult = try {
            continuousAmapLocationManager.getCurrentLocation(boundedTimeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_ERROR,
                throwable = e,
                extras = mapOf("errorMsg" to e.message)
            )
            null
        }
        if (amapResult != null) {
            locationStateManager.recordLocationSuccess(amapResult)
        }
        return amapResult
    }

    override fun getCachedLocation(maxAgeMs: Long): LocationResult? {
        return locationStateManager.getValidLocation(maxAgeMs)
    }

    override fun acquireKeepAlive(owner: String) {
        locationKeepAliveManager.acquire(owner)
    }

    override fun releaseKeepAlive(owner: String) {
        locationKeepAliveManager.release(owner)
    }

    override fun notifyPermissionGranted() {
        continuousAmapLocationManager.restartAfterPermissionGrant()
    }

    private companion object {
        const val MIN_LOCATION_TIMEOUT_MS = 1_000L
        const val MAX_LOCATION_TIMEOUT_MS = LocationFacade.DEFAULT_FAST_LOCATION_TIMEOUT_MS
    }
}
