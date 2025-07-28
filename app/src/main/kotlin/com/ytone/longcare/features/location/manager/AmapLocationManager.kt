package com.ytone.longcare.features.location.manager

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 高德定位管理器
 * 封装高德定位SDK的使用，提供简洁的定位接口
 */
@Singleton
class AmapLocationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var locationClient: AMapLocationClient? = null
    private var isInitialized = false

    /**
     * 初始化高德定位客户端
     */
    private fun initLocationClient() {
        if (isInitialized) return
        
        try {
            // 设置高德地图API Key
            AMapLocationClient.setApiKey(BuildConfig.AMAP_API_KEY)
            // 设置隐私合规
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            
            // 初始化定位客户端
            locationClient = AMapLocationClient(context)
            
            // 配置定位参数
            val option = AMapLocationClientOption().apply {
                // 设置定位模式为高精度模式
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 设置是否返回地址信息（可选）
                isNeedAddress = false
                // 设置是否只定位一次
                isOnceLocation = true
                // 设置是否强制刷新WIFI，默认为true，强制刷新
                isWifiScan = true
                // 设置是否允许模拟位置，默认为false，不允许
                isMockEnable = false
                // 设置定位间隔，单位毫秒（单次定位时无效）
                interval = 30000
                // 设置定位超时时间，单位毫秒
                httpTimeOut = 20000
            }
            
            locationClient?.setLocationOption(option)
            isInitialized = true
            logI("高德定位客户端初始化成功")
        } catch (e: Exception) {
            logE("高德定位客户端初始化失败: ${e.message}")
        }
    }

    /**
     * 获取当前位置（协程版本）
     * @return 定位结果，包含经纬度信息，失败时返回null
     */
    suspend fun getCurrentLocation(): LocationResult? = suspendCancellableCoroutine { continuation ->
        initLocationClient()
        
        val client = locationClient
        if (client == null) {
            logE("高德定位客户端未初始化")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val listener = object : AMapLocationListener {
            override fun onLocationChanged(location: AMapLocation?) {
                // 移除监听器
                client.unRegisterLocationListener(this)
                
                if (location != null && location.errorCode == 0) {
                    // 定位成功
                    logI("高德定位成功: Provider=${location.provider}, Lat=${location.latitude}, Lng=${location.longitude}")
                    val result = LocationResult(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        provider = "amap",
                        accuracy = location.accuracy
                    )
                    continuation.resume(result)
                } else {
                    // 定位失败
                    val errorMsg = location?.errorInfo ?: "未知错误"
                    logE("高德定位失败: ${location?.errorCode} - $errorMsg")
                    continuation.resume(null)
                }
            }
        }

        // 设置定位监听器
        client.setLocationListener(listener)
        
        // 启动定位
        client.startLocation()
        
        // 设置取消回调
        continuation.invokeOnCancellation {
            client.unRegisterLocationListener(listener)
            client.stopLocation()
        }
    }

    /**
     * 销毁定位客户端
     */
    fun destroy() {
        locationClient?.onDestroy()
        locationClient = null
        isInitialized = false
        logI("高德定位客户端已销毁")
    }

    /**
     * 定位结果数据类
     */
    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val provider: String,
        val accuracy: Float = 0f
    )
}