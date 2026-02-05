package com.ytone.longcare.features.location.viewmodel

import androidx.lifecycle.ViewModel
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LocationTrackingViewModel @Inject constructor(
    private val trackingManager: LocationTrackingManager
) : ViewModel() {

    /**
     * 直接将 Manager 的追踪状态暴露给UI层。
     */
    val isTracking: StateFlow<Boolean> = trackingManager.isTracking
    
    /**
     * 当前正在追踪的订单请求模型。
     */
    val currentTrackingRequest: StateFlow<OrderInfoRequestModel?> = trackingManager.currentTrackingRequest

    /**
     * 当UI层的"开启"按钮被点击时调用。
     * 将操作委托给 Manager。
     */
    fun onStartClicked(request: OrderInfoRequestModel) {
        trackingManager.startTracking(request)
    }

    /**
     * 当UI层的"结束"按钮被点击时调用。
     * 将操作委托给 Manager。
     */
    fun onStopClicked() {
        trackingManager.stopTracking()
    }
    
    /**
     * 强制停止定位追踪服务。
     * 无论当前状态如何，都会发送停止命令。
     * 用于异常情况下确保服务被停止。
     */
    fun forceStop() {
        trackingManager.forceStopTracking()
    }
}
