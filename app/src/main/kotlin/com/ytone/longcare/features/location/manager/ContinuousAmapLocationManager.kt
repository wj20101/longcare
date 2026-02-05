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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    fun startContinuousLocation(
        interval: Long = DEFAULT_INTERVAL
    ): Flow<LocationResult> = callbackFlow {
        val third = systemConfigManager.getThirdKey()
        val apiKey = third?.gaoDeMapApiKey?.takeIf { it.isNotBlank() } ?: ""
        
        if (apiKey.isBlank()) {
            logE("高德定位API Key不可用")
            close()
            return@callbackFlow
        }
        
        initContinuousLocationClient(apiKey, interval)
        
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
        logI("持续定位已启动")
        
        awaitClose {
            logI("持续定位已停止")
            client.unRegisterLocationListener(listener)
            client.stopLocation()
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
}
