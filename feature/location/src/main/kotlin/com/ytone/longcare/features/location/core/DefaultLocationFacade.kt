package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationSampleStore
import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLocationFacade @Inject constructor(
    private val continuousAmapLocationManager: ContinuousAmapLocationManager,
    private val locationSampleStore: LocationSampleStore,
    private val locationKeepAliveManager: LocationKeepAliveManager
) : LocationFacade {

    override suspend fun getCurrentLocation(timeoutMs: Long): LocationResult? {
        locationSampleStore.getValidLocation(LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS)?.let { return it }

        val boundedTimeoutMs = timeoutMs.coerceIn(
            LocationFacade.MIN_FAST_LOCATION_TIMEOUT_MS,
            LocationFacade.MAX_FAST_LOCATION_TIMEOUT_MS,
        )

        val amapResult = try {
            continuousAmapLocationManager.getCurrentLocation(boundedTimeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            null
        }
        if (amapResult != null) {
            locationSampleStore.record(amapResult)
        }
        return amapResult
    }

    override suspend fun getFreshLocation(timeoutMs: Long): LocationResult? {
        val boundedTimeoutMs = timeoutMs.coerceIn(
            LocationFacade.MIN_FRESH_LOCATION_TIMEOUT_MS,
            LocationFacade.MAX_FRESH_LOCATION_TIMEOUT_MS,
        )

        val amapResult = try {
            continuousAmapLocationManager.getFreshLocation(boundedTimeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            null
        }
        if (amapResult != null) {
            locationSampleStore.record(amapResult)
        }
        return amapResult
    }

    override fun getCachedLocation(maxAgeMs: Long): LocationResult? {
        return locationSampleStore.getValidLocation(maxAgeMs)
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
}
