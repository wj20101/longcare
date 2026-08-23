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
     * 当UI层的"开启"按钮被点击时调用。
     * 将操作委托给 Manager。
     */
    fun startTracking(orderKey: OrderKey) {
        trackingManager.startTracking(orderKey)
    }

    /**
     * 定位权限刚被授予后调用，重启定位引擎再启动追踪。
     */
    fun startTrackingAfterPermissionGrant(orderKey: OrderKey) {
        trackingManager.startTrackingAfterPermissionGrant(orderKey)
    }

    /**
     * 当UI层的"结束"按钮被点击时调用。
     * 订单结束后清理上报任务与其前台定位 owner。
     */
    fun stopTracking() {
        trackingManager.stopTracking()
    }
}
