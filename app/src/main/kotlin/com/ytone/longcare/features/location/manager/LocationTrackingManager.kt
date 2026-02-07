package com.ytone.longcare.features.location.manager

import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.core.LocationFacade
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 兼容层：维持既有调用方式，同时将“定位保活”和“位置上报”解耦。
 *
 * - 定位保活：由 LocationFacade/LocationKeepAliveManager 负责
 * - 位置上报：由 LocationReportingManager 负责
 */
@Singleton
class LocationTrackingManager @Inject constructor(
    private val locationFacade: LocationFacade,
    private val locationReportingManager: LocationReportingManager
) {
    val isTracking = locationReportingManager.isTracking

    val currentTrackingRequest = locationReportingManager.currentTrackingRequest

    private var isUiActive = false

    companion object {
        private const val UI_SESSION_OWNER = "location_ui_session"
    }

    /**
     * 开启定位会话（仅保活，不包含上报业务）。
     */
    fun startLocationSession() {
        isUiActive = true
        locationFacade.acquireKeepAlive(UI_SESSION_OWNER)
        logI("定位会话已开启（保活）")
    }

    /**
     * 结束定位会话（仅释放 UI 保活）。
     */
    fun stopLocationSession() {
        isUiActive = false
        locationFacade.releaseKeepAlive(UI_SESSION_OWNER)
        logI("定位会话已结束（保活释放）")
    }

    /**
     * 启动位置上报业务。
     */
    fun startTracking(request: OrderInfoRequestModel) {
        locationReportingManager.startReporting(request)
    }

    /**
     * 停止位置上报业务。
     */
    fun stopTracking() {
        locationReportingManager.stopReporting()
    }

    /**
     * 强制停止位置上报业务。
     */
    fun forceStopTracking() {
        logI("强制停止位置上报")
        locationReportingManager.forceStopReporting()
        if (!isUiActive) {
            locationFacade.releaseKeepAlive(UI_SESSION_OWNER)
        }
    }
}
