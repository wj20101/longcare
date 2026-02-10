package com.ytone.longcare.features.location.core

import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.features.location.provider.LocationResult
import com.ytone.longcare.features.location.provider.SystemLocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLocationFacade @Inject constructor(
    private val continuousAmapLocationManager: ContinuousAmapLocationManager,
    private val locationStateManager: LocationStateManager,
    private val systemLocationProvider: SystemLocationProvider,
    private val locationKeepAliveManager: LocationKeepAliveManager
) : LocationFacade {

    override fun observeLocations(intervalMs: Long): Flow<LocationResult> {
        return continuousAmapLocationManager.startContinuousLocation(intervalMs)
    }

    override suspend fun getCurrentLocation(timeoutMs: Long): LocationResult? {
        locationStateManager.getValidLocation()?.let { return it }

        val amapResult = try {
            continuousAmapLocationManager.getCurrentLocation(timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("高德单次定位异常: ${e.message}")
            null
        }
        if (amapResult != null) {
            locationStateManager.recordLocationSuccess(amapResult)
            return amapResult
        }

        val systemResult = try {
            systemLocationProvider.getCurrentLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("系统单次定位异常: ${e.message}")
            null
        }
        if (systemResult != null) {
            locationStateManager.recordLocationSuccess(systemResult)
        }
        return systemResult
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
}
