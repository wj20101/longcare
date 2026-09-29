package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/** 单次请求不读取历史业务缓存，也不改变 Service 独占的持续采集。 */
interface LocationFacade {
    suspend fun acquireCurrentLocation(): LocationAcquisition

    /** 在实际提交/拍摄时复核，包含实时权限、开关和样本时效。 */
    fun isUsable(location: LocationResult): Boolean

    fun acquireKeepAlive(owner: String)
    fun releaseKeepAlive(owner: String)
    fun notifyPermissionGranted()
}

sealed interface LocationAcquisition {
    data class Success(val location: LocationResult) : LocationAcquisition
    data class Failure(val reason: LocationFailure) : LocationAcquisition
}

enum class LocationFailure {
    PERMISSION, SERVICE_DISABLED, BUSY, TIMEOUT, QUALITY, NETWORK, CONFIGURATION, UNAVAILABLE,
}
