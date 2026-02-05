package com.ytone.longcare.features.location.manager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ytone.longcare.features.location.provider.LocationResult
import com.ytone.longcare.features.location.service.LocationTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 定位状态管理器
 * 
 * 增强版的定位状态中心，提供：
 * 1. 追踪状态管理
 * 2. 最后位置信息
 * 3. 统计数据（成功/失败次数）
 * 4. 错误状态跟踪
 * 
 * 替代原有的LocationTrackingManager，提供更丰富的状态信息。
 */
@Singleton
class LocationStateManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // ========== 状态定义 ==========
    
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state.asStateFlow()
    
    /**
     * 当前是否正在追踪
     */
    val isTracking: StateFlow<Boolean> 
        get() = MutableStateFlow(_state.value.isTracking).asStateFlow()
    
    // ========== 服务控制 ==========
    
    /**
     * 启动定位追踪服务
     */
    fun startTracking(orderId: Long) {
        if (_state.value.isTracking) return
        
        _state.update { it.copy(
            isTracking = true,
            currentOrderId = orderId,
            startTime = System.currentTimeMillis(),
            error = null
        )}
        
        Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_ORDER_ID, orderId)
        }.also {
            ContextCompat.startForegroundService(context, it)
        }
    }
    
    /**
     * 停止定位追踪服务
     */
    fun stopTracking() {
        if (!_state.value.isTracking) return
        
        _state.update { it.copy(
            isTracking = false,
            currentOrderId = null,
            startTime = null
        )}
        
        Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }.also {
            context.startService(it)
        }
    }
    
    /**
     * 强制停止定位追踪服务
     */
    fun forceStopTracking() {
        _state.update { it.copy(
            isTracking = false,
            currentOrderId = null,
            startTime = null
        )}
        
        Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }.also {
            context.startService(it)
        }
    }
    
    // ========== 状态更新（由Service调用） ==========
    
    /**
     * 更新追踪状态
     */
    internal fun updateTrackingState(isTracking: Boolean) {
        _state.update { it.copy(isTracking = isTracking) }
    }
    
    /**
     * 记录定位成功
     */
    internal fun recordLocationSuccess(location: LocationResult) {
        _state.update { state ->
            state.copy(
                lastLocation = location,
                lastLocationTime = System.currentTimeMillis(),
                successCount = state.successCount + 1,
                error = null
            )
        }
    }
    
    /**
     * 记录定位失败
     */
    internal fun recordLocationFailure(errorMessage: String) {
        _state.update { state ->
            state.copy(
                failureCount = state.failureCount + 1,
                error = LocationError(
                    message = errorMessage,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
    
    /**
     * 重置统计数据
     */
    fun resetStats() {
        _state.update { it.copy(
            successCount = 0,
            failureCount = 0,
            lastLocation = null,
            lastLocationTime = null,
            error = null
        )}
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 获取运行时长（毫秒）
     */
    fun getRunningDuration(): Long? {
        val startTime = _state.value.startTime ?: return null
        return if (_state.value.isTracking) {
            System.currentTimeMillis() - startTime
        } else {
            null
        }
    }
    
    /**
     * 获取成功率
     */
    fun getSuccessRate(): Float {
        val state = _state.value
        val total = state.successCount + state.failureCount
        return if (total > 0) {
            state.successCount.toFloat() / total
        } else {
            0f
        }
    }
}

/**
 * 定位状态数据类
 */
data class LocationState(
    /** 是否正在追踪 */
    val isTracking: Boolean = false,
    /** 当前订单ID */
    val currentOrderId: Long? = null,
    /** 追踪开始时间 */
    val startTime: Long? = null,
    /** 最后获取的位置 */
    val lastLocation: LocationResult? = null,
    /** 最后位置获取时间 */
    val lastLocationTime: Long? = null,
    /** 成功次数 */
    val successCount: Int = 0,
    /** 失败次数 */
    val failureCount: Int = 0,
    /** 错误信息 */
    val error: LocationError? = null
)

/**
 * 定位错误信息
 */
data class LocationError(
    val message: String,
    val timestamp: Long
)
