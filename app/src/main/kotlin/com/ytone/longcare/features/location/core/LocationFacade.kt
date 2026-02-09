package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.provider.LocationResult
import kotlinx.coroutines.flow.Flow

/**
 * 统一对外暴露定位能力：
 * 1. 获取实时位置流
 * 2. 单次定位
 * 3. 获取缓存位置
 * 4. 管理定位保活生命周期
 */
interface LocationFacade {
    fun observeLocations(intervalMs: Long = 30_000L): Flow<LocationResult>

    suspend fun getCurrentLocation(timeoutMs: Long = 10_000L): LocationResult?

    fun getCachedLocation(maxAgeMs: Long = 30_000L): LocationResult?

    fun acquireKeepAlive(owner: String)

    fun releaseKeepAlive(owner: String)
}
