package com.ytone.longcare.features.location.provider

import android.location.Location

/**
 * 定位策略枚举
 */
enum class LocationStrategy {
    SYSTEM,  // 系统定位
    AMAP,    // 高德定位
    AUTO     // 自动选择（优先高德，失败时回退到系统）
}

/**
 * 定位结果数据类
 */
data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracy: Float = 0f
) {
    companion object {
        fun fromSystemLocation(location: Location): LocationResult {
            return LocationResult(
                latitude = location.latitude,
                longitude = location.longitude,
                provider = "system_${location.provider}",
                accuracy = location.accuracy
            )
        }
    }
}

/**
 * 定位提供者接口
 */
interface LocationProvider {
    /**
     * 获取当前位置
     * @return 定位结果，失败时返回null
     */
    suspend fun getCurrentLocation(): LocationResult?
    
    /**
     * 销毁资源
     */
    fun destroy()
}