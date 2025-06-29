package com.ytone.longcare.features.location.viewmodel

import androidx.lifecycle.ViewModel
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
     * 当UI层的“开启”按钮被点击时调用。
     * 将操作委托给 Manager。
     */
    fun onStartClicked(orderId: Long) {
        trackingManager.startTracking(orderId)
    }

    /**
     * 当UI层的“结束”按钮被点击时调用。
     * 将操作委托给 Manager。
     */
    fun onStopClicked() {
        trackingManager.stopTracking()
    }
}