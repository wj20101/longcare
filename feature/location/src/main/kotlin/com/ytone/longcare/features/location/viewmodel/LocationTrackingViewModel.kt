package com.ytone.longcare.features.location.viewmodel

import androidx.lifecycle.ViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LocationTrackingViewModel @Inject constructor(
    private val trackingManager: LocationTrackingManager
) : ViewModel() {

    /**
     * 进入服务订单且定位权限就绪时调用；业务层确认状态后才允许上报。
     */
    fun startTracking(orderKey: OrderKey) {
        trackingManager.startTracking(orderKey)
    }

    fun onOrderStarted(orderId: Long) {
        trackingManager.onOrderStarted(orderId)
    }

    /**
     * 定位权限刚被授予后调用，重启定位引擎再启动追踪。
     */
    fun startTrackingAfterPermissionGrant(orderKey: OrderKey) {
        trackingManager.startTrackingAfterPermissionGrant(orderKey)
    }

    /**
     * 用于已确认的业务停止/退出；不能在仅点击“结束”进入确认流程时调用。
     */
    fun stopTracking() {
        trackingManager.stopTracking()
    }
}
