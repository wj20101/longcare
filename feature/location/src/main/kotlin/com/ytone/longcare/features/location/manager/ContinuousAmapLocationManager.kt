package com.ytone.longcare.features.location.manager

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.model.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

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
    private val amapApiKeyProvider: AmapApiKeyProvider
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
        const val MIN_QUICK_TIMEOUT = LocationFacade.MIN_FAST_LOCATION_TIMEOUT_MS
        const val MAX_QUICK_TIMEOUT = LocationFacade.MAX_FAST_LOCATION_TIMEOUT_MS
        /** NFC新鲜定位最小超时，低于高德建议值时自动提升 */
        const val MIN_FRESH_TIMEOUT = LocationFacade.MIN_FRESH_LOCATION_TIMEOUT_MS
        /** NFC新鲜定位最大超时，避免用户在扫码后等待过久 */
        const val MAX_FRESH_TIMEOUT = LocationFacade.MAX_FRESH_LOCATION_TIMEOUT_MS
        private const val CONTINUOUS_HTTP_TIMEOUT_MILLIS = 20_000L
        private const val PROVIDER_CONTINUOUS = "amap_continuous"
        private const val PROVIDER_QUICK = "amap_quick"
        private const val PROVIDER_FRESH = "amap_fresh"
    }

    @Volatile
    private var currentIntervalMs: Long = DEFAULT_INTERVAL
    
    // 缓存待绑定的通知，用于解决初始化时序问题
    private var pendingNotification: Pair<Int, android.app.Notification>? = null
    
    /**
     * 初始化持续定位客户端
     * 
     * @param interval 定位间隔（毫秒），默认30秒
     */
    @Synchronized
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
            val coercedInterval = interval.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
            currentIntervalMs = coercedInterval
            locationClient?.setLocationOption(buildContinuousLocationOption(coercedInterval))
            isInitialized = true
            logI("持续高德定位客户端初始化成功，间隔: ${coercedInterval}ms")
            
            // 如果有待绑定的后台通知，立即应用
            pendingNotification?.let { (id, notification) ->
                locationClient?.enableBackgroundLocation(id, notification)
                logI("初始化时应用后台定位保活 (NotificationId: $id)")
            }
        } catch (e: Exception) {
            runCatching { locationClient?.onDestroy() }
            locationClient = null
            isInitialized = false
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.CLIENT_INIT_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            throw e
        }
    }

    private fun buildContinuousLocationOption(intervalMs: Long): AMapLocationClientOption {
        return AMapLocationClientOption().apply {
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
            interval = intervalMs.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
            // 设置定位超时时间
            httpTimeOut = CONTINUOUS_HTTP_TIMEOUT_MILLIS
        }
    }

    private fun buildFreshLocationOption(timeoutMs: Long): AMapLocationClientOption {
        return AMapLocationClientOption().apply {
            locationPurpose = AMapLocationClientOption.AMapLocationPurpose.SignIn
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = false
            isOnceLocation = true
            isOnceLocationLatest = true
            isWifiScan = true
            isMockEnable = false
            isLocationCacheEnable = false
            httpTimeOut = timeoutMs.coerceIn(MIN_FRESH_TIMEOUT, MAX_FRESH_TIMEOUT)
        }
    }

    private fun buildQuickLocationOption(timeoutMs: Long): AMapLocationClientOption {
        return AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = false
            isOnceLocation = true
            isOnceLocationLatest = true
            isWifiScan = true
            isMockEnable = false
            isLocationCacheEnable = false
            httpTimeOut = timeoutMs.coerceIn(MIN_QUICK_TIMEOUT, MAX_QUICK_TIMEOUT)
        }
    }
    
    /**
     * 开始持续定位并返回位置更新Flow
     * 
     * @return 位置更新Flow，收集时自动开始定位，取消收集时自动停止
     */
    /** Cold flow collected only by LocationTrackingService. */
    private val _locationFlow = callbackFlow {
        val apiKey = amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() } ?: ""
        
        if (apiKey.isBlank()) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.API_KEY_UNAVAILABLE)
            close(IllegalStateException("amap_api_key_unavailable"))
            return@callbackFlow
        }
        
        // 确保初始化，使用最新配置间隔
        initContinuousLocationClient(apiKey, currentIntervalMs)
        
        val client = locationClient
        if (client == null) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.CLIENT_NOT_INITIALIZED)
            close(IllegalStateException("amap_client_not_initialized"))
            return@callbackFlow
        }
        
        val listener = AMapLocationListener { location: AMapLocation? ->
            if (location != null && location.errorCode == 0) {
                logI("持续定位更新已接收")
                val result = LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    provider = PROVIDER_CONTINUOUS,
                    accuracy = location.accuracy,
                    coordType = location.coordType.orEmpty(),
                    locationType = location.locationType,
                    trustedLevel = location.trustedLevel,
                    locationTime = location.time
                )
                trySend(result)
            } else {
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_ERROR,
                    extras = mapOf(
                        LocationEventTracker.Attribute.ERROR_CODE to location?.errorCode,
                    )
                )
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
    }

    /**
     * 获取持续定位流
     * 
     * @param interval 定位间隔（毫秒）
     * @return 由前台 Service 独占收集的位置更新 Flow
     */
    fun startContinuousLocation(
        interval: Long = DEFAULT_INTERVAL
    ): Flow<LocationResult> {
        // 更新间隔配置（如果有变化）
        updateInterval(interval)
        return _locationFlow
    }

    /** Fast isolated snapshot. It never becomes a second continuous-flow collector. */
    suspend fun getCurrentLocation(
        timeoutMs: Long = LocationFacade.DEFAULT_FAST_LOCATION_TIMEOUT_MS,
    ): LocationResult? {
        val boundedTimeoutMs = timeoutMs.coerceIn(MIN_QUICK_TIMEOUT, MAX_QUICK_TIMEOUT)
        return getIsolatedLocation(
            timeoutMs = boundedTimeoutMs,
            provider = PROVIDER_QUICK,
            option = buildQuickLocationOption(boundedTimeoutMs),
        )
    }

    suspend fun getFreshLocation(timeoutMs: Long = LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult? {
        val boundedTimeoutMs = timeoutMs.coerceIn(MIN_FRESH_TIMEOUT, MAX_FRESH_TIMEOUT)
        return getIsolatedLocation(
            timeoutMs = boundedTimeoutMs,
            provider = PROVIDER_FRESH,
            option = buildFreshLocationOption(boundedTimeoutMs),
        )
    }

    private suspend fun getIsolatedLocation(
        timeoutMs: Long,
        provider: String,
        option: AMapLocationClientOption,
    ): LocationResult? {
        val apiKey = amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() } ?: ""

        if (apiKey.isBlank()) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.API_KEY_UNAVAILABLE)
            return null
        }

        return try {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                suspendCancellableCoroutine { continuation ->
                    AMapLocationClient.setApiKey(apiKey)
                    AMapLocationClient.updatePrivacyShow(context, true, true)
                    AMapLocationClient.updatePrivacyAgree(context, true)

                    val finished = AtomicBoolean(false)
                    var client: AMapLocationClient? = null
                    var listener: AMapLocationListener? = null

                    fun cleanup() {
                        try {
                            listener?.let { client?.unRegisterLocationListener(it) }
                            client?.stopLocation()
                            client?.onDestroy()
                        } catch (e: Exception) {
                            LocationEventTracker.trackError(
                                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                                throwable = e,
                                extras = mapOf(
                                    LocationEventTracker.Attribute.ERROR_TYPE to
                                        e.javaClass.simpleName,
                                )
                            )
                        }
                    }

                    fun finish(result: LocationResult?) {
                        if (!finished.compareAndSet(false, true)) return
                        cleanup()
                        continuation.resume(result)
                    }

                    try {
                        val isolatedClient = AMapLocationClient(context)
                        client = isolatedClient
                        listener = AMapLocationListener { location: AMapLocation? ->
                            if (location != null && location.errorCode == 0) {
                                finish(
                                    LocationResult(
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        provider = provider,
                                        accuracy = location.accuracy,
                                        coordType = location.coordType.orEmpty(),
                                        locationType = location.locationType,
                                        trustedLevel = location.trustedLevel,
                                        locationTime = location.time
                                    )
                                )
                            } else {
                                LocationEventTracker.trackError(
                                    LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                                    extras = mapOf(
                                        LocationEventTracker.Attribute.ERROR_CODE to
                                            location?.errorCode,
                                    )
                                )
                                finish(null)
                            }
                        }
                        isolatedClient.setLocationListener(listener)
                        isolatedClient.setLocationOption(option)
                        isolatedClient.startLocation()
                    } catch (e: Exception) {
                        LocationEventTracker.trackError(
                            LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                            throwable = e,
                            extras = mapOf(
                                LocationEventTracker.Attribute.ERROR_TYPE to
                                    e.javaClass.simpleName,
                            )
                        )
                        finish(null)
                    }

                    continuation.invokeOnCancellation {
                        if (finished.compareAndSet(false, true)) {
                            cleanup()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            null
        }
    }

    /**
     * 权限授予后重启定位引擎。
     * AMap SDK 在无权限时启动会进入错误状态，授权后需要 stop+start 才能恢复。
     */
    @Synchronized
    fun restartAfterPermissionGrant() {
        val client = locationClient ?: return
        logI("权限变更，重启高德定位引擎")
        client.stopLocation()
        client.startLocation()
    }

    /**
     * 停止持续定位
     */
    @Synchronized
    fun stopContinuousLocation() {
        locationClient?.stopLocation()
        logI("持续高德定位已手动停止")
    }
    
    /**
     * 销毁定位客户端
     */
    @Synchronized
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
    @Synchronized
    fun updateInterval(interval: Long) {
        val coercedInterval = interval.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        currentIntervalMs = coercedInterval
        locationClient?.setLocationOption(buildContinuousLocationOption(coercedInterval))
        logI("定位间隔已更新为: ${coercedInterval}ms")
    }

    /**
     * 开启后台定位（绑定前台服务通知）
     * 解决锁屏后网络定位失败(Error 13)的问题
     *
     * @param notificationId 通知的ID
     * @param notification 通知对象
     */
    @Synchronized
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
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.ENABLE_BACKGROUND_LOCATION_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
        }
    }

    /**
     * 关闭后台定位
     *
     * @param removeNotification 是否移除通知
     */
    @Synchronized
    fun disableBackgroundLocation(removeNotification: Boolean) {
        pendingNotification = null
        if (locationClient == null) return
        try {
            locationClient?.disableBackgroundLocation(removeNotification)
            logI("已关闭后台定位保活")
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.DISABLE_BACKGROUND_LOCATION_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
        }
    }
}
