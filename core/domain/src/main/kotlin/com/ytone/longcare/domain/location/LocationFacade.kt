package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/** 动作取点与提交前质量复核；服务订单借用当前采集流，通用取点独立采集。 */
interface LocationFacade {
    suspend fun acquireCurrentLocation(): LocationAcquisition

    /** 服务订单专用：200 米精度上限，最多等待 30 秒；不接受动作窗口外的位置。 */
    suspend fun acquireServiceOrderLocation(): LocationAcquisition

    /** 实际提交前复核权限、开关及质量，返回 null 表示可用；不使用固定 15 秒提交时限。 */
    fun serviceOrderLocationFailure(location: LocationResult): LocationFailure?

    /** 在实际提交/拍摄时复核，包含实时权限、开关和样本时效。 */
    fun isUsable(location: LocationResult): Boolean

    fun notifyPermissionGranted()
}

sealed interface LocationAcquisition {
    data class Success(val location: LocationResult) : LocationAcquisition
    data class Failure(val reason: LocationFailure, val location: LocationResult? = null) : LocationAcquisition
}

enum class LocationFailure {
    PERMISSION, SERVICE_DISABLED, BUSY, TIMEOUT, QUALITY, PRECISION, NETWORK, CONFIGURATION, UNAVAILABLE,
}
