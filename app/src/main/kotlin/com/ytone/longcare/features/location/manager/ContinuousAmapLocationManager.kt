package com.ytone.longcare.features.location.manager

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.provider.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 持续高德定位管理器
 * 
 * 与AmapLocationManager（单次定位）不同，该管理器提供：
 * 1. 持续定位模式（指定间隔自动更新）
 * 2. Flow形式的位置更新流
 * 3. 适用于需要连续追踪的场景
 */
@Singleton
class ContinuousAmapLocationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val systemConfigManager: SystemConfigManager
) {
    private var locationClient: AMapLocationClient? = null
    private var isInitialized = false
    
    companion object {
        /** 默认定位间隔（毫秒） */
        const val DEFAULT_INTERVAL = 30_000L
        /** 最小定位间隔（毫秒） */
        const val MIN_INTERVAL = 5_000L
        /** 最大定位间隔（毫秒） */
        const val MAX_INTERVAL = 120_000L
    }
    
    // 缓存待绑定的通知，用于解决初始化时序问题
    private var pendingNotification: Pair<Int, android.app.Notification>? = null
    
    /**
     * 初始化持续定位客户端
     * 
     * @param interval 定位间隔（毫秒），默认30秒
     */
    private fun initContinuousLocationClient(
        apiKey: String,
        interval: Long = DEFAULT_INTERVAL
    ) {
        if (isInitialized) return
        
        try {
            // 设置高德地图API Key
            AMapLocationClient.setApiKey(apiKey)
            // 设置隐私合规
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            
            // 初始化定位客户端
            locationClient = AMapLocationClient(context)
            
            // 配置持续定位参数
            val option = AMapLocationClientOption().apply {
                // 设置定位模式为高精度模式
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 设置是否返回地址信息
                isNeedAddress = false
                // 关闭单次定位，开启持续定位
                isOnceLocation = false
                // 设置是否强制刷新WIFI
                isWifiScan = true
                // 设置是否允许模拟位置
                isMockEnable = false
                // 设置定位间隔
                this.interval = interval.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
                // 设置定位超时时间
                httpTimeOut = 20000
            }
            
            locationClient?.setLocationOption(option)
            isInitialized = true
            logI("持续高德定位客户端初始化成功，间隔: ${interval}ms")
            
            // 如果有待绑定的后台通知，立即应用
            pendingNotification?.let { (id, notification) ->
                locationClient?.enableBackgroundLocation(id, notification)
                logI("初始化时应用后台定位保活 (NotificationId: $id)")
            }
        } catch (e: Exception) {
            logE("持续高德定位客户端初始化失败: ${e.message}")
        }
    }
    
    /**
     * 开始持续定位并返回位置更新Flow
     * 
     * @param interval 定位间隔（毫秒）
     * @return 位置更新Flow，收集时自动开始定位，取消收集时自动停止
     */
    private val scope = CoroutineScope(SupervisorJob())

    /**
     * 共享的持续定位流
     * 使用 shareIn 实现多播，当订阅者 > 0 时自动启动定位，无订阅者后延时 5秒 停止定位
     */
    private val _locationFlow = callbackFlow {
        val third = systemConfigManager.getThirdKey()
        val apiKey = third?.gaoDeMapApiKey?.takeIf { it.isNotBlank() } ?: ""
        
        if (apiKey.isBlank()) {
            logE("高德定位API Key不可用")
            close()
            return@callbackFlow
        }
        
        // 确保初始化，使用当前的配置间隔
        initContinuousLocationClient(apiKey, DEFAULT_INTERVAL)
        
        val client = locationClient
        if (client == null) {
            logE("持续高德定位客户端未初始化")
            close()
            return@callbackFlow
        }
        
        val listener = AMapLocationListener { location: AMapLocation? ->
            if (location != null && location.errorCode == 0) {
                logI("持续定位更新: Lat=${location.latitude}, Lng=${location.longitude}")
                val result = LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    provider = "amap_continuous",
                    accuracy = location.accuracy
                )
                trySend(result)
            } else {
                val errorMsg = location?.errorInfo ?: "未知错误"
                logE("持续定位失败: ${location?.errorCode} - $errorMsg")
            }
        }
        
        client.setLocationListener(listener)
        client.startLocation()
        logI("持续定位引擎已启动 (Subscriber Added)")
        
        awaitClose {
            logI("持续定位引擎已停止 (No Subscribers)")
            client.unRegisterLocationListener(listener)
            client.stopLocation()
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), // 5秒缓冲，避免页面切换时频繁启停
        replay = 1 // 保留最新一个位置，新订阅者秒开
    )

    /**
     * 获取持续定位流
     * 
     * @param interval 定位间隔（毫秒），注意：多个订阅者将共享同一个间隔配置，后调用者会覆盖前调用者的间隔
     * @return 共享的位置更新Flow
     */
    fun startContinuousLocation(
        interval: Long = DEFAULT_INTERVAL
    ): Flow<LocationResult> {
        // 更新间隔配置（如果有变化）
        updateInterval(interval)
        return _locationFlow
    }

    /**
     * 单次获取当前位置（复用持续定位流）
     * 
     * 逻辑：
     * 1. 尝试从活动流中获取最新的位置
     * 2. 如果成功，直接返回
     * 3. 如果超时（默认10秒），返回null
     * 
     * 优势：
     * - 复用了 ContinuousAmapLocationManager 的 Flow
     * - 不会创建新的 AMapLocationClient 实例
     * - 自动处理已启动会话的情况 (StateFlow piggyback)
     */
    suspend fun getCurrentLocation(timeoutMs: Long = 10_000L): LocationResult? {
        // 如果已经有缓存且很新（Replay=1），first()会立即返回
        // 即使没有，startContinuousLocation()会触发定位（如果尚未启动）
        return try {
            withTimeoutOrNull(timeoutMs) {
                // 调用此方法会自动增加订阅者计数，触发定位启动
                startContinuousLocation().first() 
            }
        } catch (e: Exception) {
            logE("单次定位获取失败: ${e.message}")
            null
        }
    }
    
    /**
     * 停止持续定位
     */
    fun stopContinuousLocation() {
        locationClient?.stopLocation()
        logI("持续高德定位已手动停止")
    }
    
    /**
     * 销毁定位客户端
     */
    fun destroy() {
        locationClient?.onDestroy()
        locationClient = null
        isInitialized = false
        logI("持续高德定位客户端已销毁")
    }
    
    /**
     * 更新定位间隔
     * 
     * @param interval 新的定位间隔（毫秒）
     */
    fun updateInterval(interval: Long) {
        val coercedInterval = interval.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        locationClient?.setLocationOption(
            AMapLocationClientOption().apply {
                this.interval = coercedInterval
            }
        )
        logI("定位间隔已更新为: ${coercedInterval}ms")
    }

    /**
     * 开启后台定位（绑定前台服务通知）
     * 解决锁屏后网络定位失败(Error 13)的问题
     *
     * @param notificationId 通知的ID
     * @param notification 通知对象
     */
    fun enableBackgroundLocation(notificationId: Int, notification: android.app.Notification) {
        // 无论是否初始化，都缓存通知，确保重建时能自动恢复
        pendingNotification = notificationId to notification
        
        if (locationClient == null) {
            logI("已缓存后台定位通知 (客户端尚未初始化)")
            return
        }
        try {
            locationClient?.enableBackgroundLocation(notificationId, notification)
            logI("已开启后台定位保活 (NotificationId: $notificationId)")
        } catch (e: Exception) {
            logE("开启后台定位失败: ${e.message}")
        }
    }

    /**
     * 关闭后台定位
     *
     * @param removeNotification 是否移除通知
     */
    fun disableBackgroundLocation(removeNotification: Boolean) {
        pendingNotification = null
        if (locationClient == null) return
        try {
            locationClient?.disableBackgroundLocation(removeNotification)
            logI("已关闭后台定位保活")
        } catch (e: Exception) {
            logE("关闭后台定位失败: ${e.message}")
        }
    }
}
