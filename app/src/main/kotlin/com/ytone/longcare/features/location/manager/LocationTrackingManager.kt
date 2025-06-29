package com.ytone.longcare.features.location.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ytone.longcare.features.location.service.LocationTrackingService
import com.ytone.longcare.common.utils.LogExt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 定位跟踪管理器
 * 负责管理定位服务的启动、停止和状态
 */
@Singleton
class LocationTrackingManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentOrderId = MutableStateFlow(0L)
    val currentOrderId: StateFlow<Long> = _currentOrderId.asStateFlow()

    /**
     * 开始定位跟踪
     * @param orderId 订单ID
     * @return 是否成功启动
     */
    fun startTracking(orderId: Long): Boolean {
        if (!hasLocationPermission()) {
            LogExt.e("LocationTrackingManager", "No location permission")
            return false
        }

        if (_isTracking.value) {
            LogExt.w("LocationTrackingManager", "Already tracking")
            return true
        }

        if (orderId <= 0) {
            LogExt.e("LocationTrackingManager", "Invalid order ID: $orderId")
            return false
        }

        try {
            LocationTrackingService.startTracking(context, orderId)
            _isTracking.value = true
            _currentOrderId.value = orderId
            LogExt.d("LocationTrackingManager", "Started tracking for order: $orderId")
            return true
        } catch (e: Exception) {
            LogExt.e("LocationTrackingManager", "Failed to start tracking", e)
            return false
        }
    }

    /**
     * 停止定位跟踪
     */
    fun stopTracking() {
        if (!_isTracking.value) {
            LogExt.w("LocationTrackingManager", "Not tracking")
            return
        }

        try {
            LocationTrackingService.stopTracking(context)
            _isTracking.value = false
            _currentOrderId.value = 0L
            LogExt.d("LocationTrackingManager", "Stopped tracking")
        } catch (e: Exception) {
            LogExt.e("LocationTrackingManager", "Failed to stop tracking", e)
        }
    }

    /**
     * 检查是否有位置权限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取需要的权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * 重置状态（用于应用重启后恢复状态）
     */
    fun resetState() {
        _isTracking.value = false
        _currentOrderId.value = 0L
    }
}