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

}
