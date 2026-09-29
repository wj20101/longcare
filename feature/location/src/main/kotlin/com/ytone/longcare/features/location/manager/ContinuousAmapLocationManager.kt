package com.ytone.longcare.features.location.manager

import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.model.LocationResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


/** Service 持续采集与独立单次定位；两者只共享配置，不共享客户端生命周期。 */
@Singleton
class ContinuousAmapLocationManager @Inject constructor(
    private val clientFactory: AmapLocationClientFactory,
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
        private const val CONTINUOUS_HTTP_TIMEOUT_MILLIS = 20_000L
    }

    private val singleRequest = SingleLocationRequest()

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
            locationClient = clientFactory.create(apiKey)

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

    private fun baseOption() = AMapLocationClientOption().apply {
        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        isNeedAddress = false
        isWifiScan = true
        isMockEnable = false
        isLocationCacheEnable = false
        isOffset = true
    }

    private fun buildContinuousLocationOption(intervalMs: Long) = baseOption().apply {
        isOnceLocation = false
        interval = intervalMs.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        httpTimeOut = CONTINUOUS_HTTP_TIMEOUT_MILLIS
    }

    private fun buildSingleLocationOption() = baseOption().apply {
        isOnceLocation = true
        isOnceLocationLatest = true
        httpTimeOut = SingleLocationRequest.TIMEOUT_MS
    }

    private fun AMapLocation.toSample() = LocationResult(
        latitude = latitude,
        longitude = longitude,
        provider = provider.orEmpty(),
        accuracy = accuracy,
        coordType = coordType.orEmpty(),
        locationType = locationType,
        trustedLevel = trustedLevel,
        locationTime = time,
        receivedAt = System.currentTimeMillis(),
        receivedElapsedRealtime = SystemClock.elapsedRealtime(),
        isMock = isMock,
        isLastLocation = isFixLastLocation,
    )

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
                trySend(location.toSample())
            } else {
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_ERROR,
                    extras = mapOf(
                        LocationEventTracker.Attribute.ERROR_CODE to location?.errorCode,
                    )
                )
            }
        }
        
        try {
            client.setLocationListener(listener)
            client.startLocation()
            awaitClose { }
        } finally {
            try {
                client.unRegisterLocationListener(listener)
            } finally {
                client.stopLocation()
            }
        }
    }.flowOn(Dispatchers.Main.immediate)

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

    suspend fun acquireCurrentLocation(): LocationAcquisition = singleRequest.acquire {
        getIsolatedLocation()
    }

    private suspend fun getIsolatedLocation(): LocationAcquisition = withContext(Dispatchers.Main.immediate) {
        val apiKey = amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() }
            ?: return@withContext LocationAcquisition.Failure(LocationFailure.CONFIGURATION)
        var client: AMapLocationClient? = null
        var listener: AMapLocationListener? = null
        try {
            val isolatedClient = clientFactory.create(apiKey)
            client = isolatedClient
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                listener = AMapLocationListener { location ->
                    if (finished.compareAndSet(false, true)) {
                        val result = if (location != null && location.errorCode == AMapLocation.LOCATION_SUCCESS) {
                            val sample = location.toSample()
                            if (LocationQuality.rejection(sample, System.currentTimeMillis(), SystemClock.elapsedRealtime()) == null) {
                                LocationAcquisition.Success(sample)
                            } else {
                                LocationAcquisition.Failure(LocationFailure.QUALITY)
                            }
                        } else {
                            LocationAcquisition.Failure(locationFailure(location?.errorCode))
                        }
                        continuation.resume(result)
                    }
                }
                continuation.invokeOnCancellation { finished.set(true) }
                isolatedClient.setLocationListener(listener)
                isolatedClient.setLocationOption(buildSingleLocationOption())
                isolatedClient.startLocation()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            LocationAcquisition.Failure(LocationFailure.PERMISSION)
        } catch (error: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to error.javaClass.simpleName),
            )
            LocationAcquisition.Failure(LocationFailure.UNAVAILABLE)
        } finally {
            // 各释放步骤独立执行，注销监听失败也不能跳过销毁；只触及本次客户端。
            listOf<() -> Unit>(
                { listener?.let { client?.unRegisterLocationListener(it) } },
                { client?.stopLocation() },
                { client?.onDestroy() },
            ).forEach { cleanup ->
                runCatching(cleanup).onFailure {
                    LocationEventTracker.trackError(
                        LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                        extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to it.javaClass.simpleName),
                    )
                }
            }
        }
    }

    private fun locationFailure(code: Int?): LocationFailure = when (code) {
        AMapLocation.ERROR_CODE_FAILURE_LOCATION_PERMISSION,
        AMapLocation.ERROR_CODE_FAILURE_COARSE_LOCATION -> LocationFailure.PERMISSION
        AMapLocation.ERROR_CODE_FAILURE_AUTH,
        AMapLocation.ERROR_CODE_FAILURE_INIT,
        AMapLocation.ERROR_CODE_SERVICE_FAIL,
        AMapLocation.ERROR_CODE_INVALID_PARAMETER -> LocationFailure.CONFIGURATION
        AMapLocation.ERROR_CODE_FAILURE_CONNECTION -> LocationFailure.NETWORK
        AMapLocation.ERROR_CODE_FAILURE_SIMULATION_LOCATION,
        AMapLocation.ERROR_CODE_FAILURE_WIFI_INFO -> LocationFailure.QUALITY
        else -> LocationFailure.UNAVAILABLE
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
