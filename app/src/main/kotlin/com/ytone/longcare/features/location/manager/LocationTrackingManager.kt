package com.ytone.longcare.features.location.manager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.service.LocationTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    @param:ApplicationContext private val context: Context,
    private val continuousAmapLocationManager: ContinuousAmapLocationManager,
    private val locationStateManager: LocationStateManager
) {
    private val _isTracking = MutableStateFlow(false)
    /**
     * UI和ViewModel可以订阅此StateFlow来实时获取追踪状态。
     */
    val isTracking = _isTracking.asStateFlow()

    private val _currentTrackingRequest = MutableStateFlow<OrderInfoRequestModel?>(null)
    /**
     * 当前正在追踪的订单请求模型。如果没有在追踪，则为null。
     */
    val currentTrackingRequest = _currentTrackingRequest.asStateFlow()

    // 全局协程作用域，用于维持Session期间的定位流订阅
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null

    /**
     * 开启定位会话 (Session Start)
     * 
     * 业务场景: 进入工单流程的第一步 (NursingExecutionScreen) 时调用。
     * 作用: 只要Session开启，ContinuousAmapLocationManager 就会保持活跃，
     * 无论页面如何跳转，是否有其他订阅者，都不会停止定位。
     */
    fun startLocationSession() {
        if (sessionJob?.isActive == true) {
            logI("定位会话(Session)已存在，跳过启动 (sessionJob=$sessionJob)")
            return
        }
        
        logE("🚀 启动定位会话 (Session Start)...", tag = "LocSession")
        logI("🚀 启动定位会话 (Session Start)...")
        // 启动一个长期运行的Job来订阅定位流
        // 因为 ContinuousAmapLocationManager 使用 shareIn(started = WhileSubscribed)，
        // 只要有至少一个订阅者，它就会保持定位开启。
        sessionJob = sessionScope.launch {
            logE("Session Job Started, subscribing to location flow...", tag = "LocSession")
            logI("Session Job Started, subscribing to location flow...")
            try {
                continuousAmapLocationManager.startContinuousLocation()
                    .collect { location ->
                        logE("Session received location: ${location.provider} ${location.latitude},${location.longitude}", tag = "LocSession")
                        // 顺便把预热的数据也记录到 StateManager，确保缓存最新
                        // logI("Session received location: $location") // Reduce log noise if frequent
                        toLocationResult(location)?.let {
                            locationStateManager.recordLocationSuccess(it)
                        } ?: locationStateManager.recordLocationSuccess(location) // Fallback if type match
                    }
            } catch (e: Exception) {
                logE("❌ 定位会话异常终止: ${e.message}", tag = "LocSession")
                logE("❌ 定位会话异常终止: ${e.message}")
                e.printStackTrace()
            } finally {
                logE("Session Job Finished", tag = "LocSession")
                logI("Session Job Finished")
            }
        }
    }

    /**
     * 结束定位会话 (Session Stop)
     * 
     * 业务场景: 
     * 1. 服务完成 (ServiceCompleteScreen)
     * 2. 中途退出回到首页 (HomeScreen)
     * 
     * 作用: 取消订阅。如果此时没有其他订阅者（如LocationTrackingService未运行），
     * ContinuousAmapLocationManager 会在5秒后自动停止定位，释放资源。
     */
    fun stopLocationSession() {
        if (sessionJob?.isActive == true) {
            logE("🛑 停止定位会话 (Session Stop)", tag = "LocSession")
            logI("🛑 停止定位会话 (Session Stop)")
            sessionJob?.cancel()
            sessionJob = null
        } else {
            logE("停止定位会话: Session inactive or null", tag = "LocSession")
            logI("停止定位会话: Session inactive or null")
        }
    }

    private fun toLocationResult(location: com.ytone.longcare.features.location.provider.LocationResult): com.ytone.longcare.features.location.provider.LocationResult? {
        return location
    }

    /**
     * 启动定位追踪服务。
     */
    fun startTracking(request: OrderInfoRequestModel) {
        if (_isTracking.value) return

        _isTracking.value = true
        Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_ORDER_REQUEST, request)
        }.also {
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
     * 强制停止定位追踪服务。
     * 无论当前状态如何，都会发送停止命令。
     * 用于异常情况下确保服务被停止。
     */
    fun forceStopTracking() {
        logI("========================================")
        logI("🛑 强制停止定位追踪服务...")
        logI("当前状态: isTracking=${_isTracking.value}")
        logI("========================================")
        
        _isTracking.value = false
        Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }.also {
            logI("📤 发送停止Intent: action=${it.action}")
            context.startService(it)
            logI("✅ 停止Intent已发送")
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

    /**
     * 更新当前正在追踪的订单请求模型。
     * internal修饰符确保了它只能在同一个模块内被调用。
     */
    internal fun setTrackingRequest(request: OrderInfoRequestModel?) {
        _currentTrackingRequest.value = request
    }
}