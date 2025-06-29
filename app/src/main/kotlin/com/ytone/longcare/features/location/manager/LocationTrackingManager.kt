package com.ytone.longcare.features.location.manager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ytone.longcare.features.location.service.LocationTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 定位追踪功能的状态中心和控制器。
 * 这是一个Hilt单例，作为整个应用中定位状态的唯一数据源。
 */
@Singleton
class LocationTrackingManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val _isTracking = MutableStateFlow(false)
    /**
     * UI和ViewModel可以订阅此StateFlow来实时获取追踪状态。
     */
    val isTracking = _isTracking.asStateFlow()

    /**
     * 启动定位追踪服务。
     */
    fun startTracking() {
        // 在这里可以添加业务判断，例如检查是否有关联的订单ID等
        // 如果当前状态已经是追踪中，则不重复发送命令
        if (_isTracking.value) return

        _isTracking.value = true
        Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }.also {
            // 使用ContextCompat.startForegroundService确保在后台也能安全启动服务
            ContextCompat.startForegroundService(context, it)
        }
    }

    /**
     * 停止定位追踪服务。
     */
    fun stopTracking() {
        // 如果当前状态已经是停止，则不重复发送命令
        if (!_isTracking.value) return

        _isTracking.value = false
        Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }.also {
            context.startService(it)
        }
    }

    /**
     * 此方法由Service在其生命周期变化时内部调用，以确保状态在任何情况下都保持同步。
     * 例如，当服务被系统杀死时，能正确地将状态更新为false。
     * internal修饰符确保了它只能在同一个模块内被调用。
     */
    internal fun updateTrackingState(isTracking: Boolean) {
        _isTracking.value = isTracking
    }
}