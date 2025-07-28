package com.ytone.longcare.features.location.provider

import com.ytone.longcare.features.location.manager.AmapLocationManager
import javax.inject.Inject

/**
 * 高德定位提供者实现
 */
class AmapLocationProvider @Inject constructor(
    private val amapLocationManager: AmapLocationManager
) : LocationProvider {
    
    override suspend fun getCurrentLocation(): LocationResult? {
        val amapResult = amapLocationManager.getCurrentLocation()
        return amapResult?.let {
            LocationResult(
                latitude = it.latitude,
                longitude = it.longitude,
                provider = it.provider,
                accuracy = it.accuracy
            )
        }
    }
    
    override fun destroy() {
        amapLocationManager.destroy()
    }
}