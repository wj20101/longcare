package com.ytone.longcare.features.location.manager

import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.model.LocationResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


/** 签到、签退借用服务中的同一采集流；销售、拍照仍使用独立单次请求。 */
@Singleton
class ContinuousAmapLocationManager @Inject constructor(
    private val clientFactory: AmapLocationClientFactory,
    private val amapApiKeyProvider: AmapApiKeyProvider
) {
    private class ContinuousClient(val client: AmapClientHandle) {
        val active = AtomicBoolean(true)
        var listener: AMapLocationListener? = null
        var backgroundEnabled = false
    }

    private var continuousClient: ContinuousClient? = null
    
    companion object {
        /** 默认定位间隔（毫秒） */
        const val DEFAULT_INTERVAL = 30_000L
        /** 最小定位间隔（毫秒） */
        const val MIN_INTERVAL = 3_000L
        private const val CONTINUOUS_HTTP_TIMEOUT_MILLIS = 20_000L
    }

    private val singleRequest = SingleLocationRequest()
    private val serviceRequest = Mutex()
    private val samples = MutableSharedFlow<LocationAcquisition>(replay = 1, extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var trackingOwner: String? = null
    private var highRequested = false
    @Volatile private var lastCallbackElapsed = 0L
    @Volatile private var lastEvent: LocationAcquisition? = null
    fun latestEvent(): LocationAcquisition? = lastEvent
    private var backgroundOwner: String? = null

    // 缓存待绑定的通知，用于解决初始化时序问题
    private var pendingNotification: Pair<Int, android.app.Notification>? = null
    
    /** 每次收集创建独立客户端，避免旧订单的在途 SDK 结果进入新监听器。 */
    @Synchronized
    private fun initContinuousLocationClient(
        apiKey: String,
    ): ContinuousClient {
        check(continuousClient == null) { "amap_continuous_collection_already_active" }
        var created: ContinuousClient? = null
        try {
            val request = ContinuousClient(clientFactory.create(apiKey))
            created = request
            continuousClient = request
            logI("持续高德定位客户端初始化成功，间隔: ${desiredInterval()}ms")
            
            // 如果有待绑定的后台通知，立即应用
            pendingNotification?.let { (id, notification) ->
                request.backgroundEnabled = true
                request.client.enableBackgroundLocation(id, notification)
                logI("初始化时应用后台定位保活 (NotificationId: $id)")
            }
            return request
        } catch (cancelled: CancellationException) {
            created?.let(::releaseContinuousClient)
            throw cancelled
        } catch (e: Exception) {
            created?.let(::releaseContinuousClient)
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.CLIENT_INIT_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            throw e
        }
    }

    @Synchronized
    private fun releaseContinuousClient(request: ContinuousClient) {
        if (!request.active.compareAndSet(true, false)) return
        if (continuousClient === request) continuousClient = null
        // 仅释放本次客户端；任一步失败仍执行后续释放，不移除 Service 持有的通知。
        val client = request.client
        listOf<Pair<String, () -> Unit>>(
            "unregister_listener" to { request.listener?.let(client::unRegisterLocationListener) },
            "stop_location" to { client.stopLocation() },
            "disable_background" to { if (request.backgroundEnabled) client.disableBackgroundLocation(false) },
            "destroy_client" to { client.onDestroy() },
        ).forEach { (stage, cleanup) ->
            runCatching(cleanup).onFailure { error ->
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.CLIENT_CLEANUP_ERROR,
                    throwable = error,
                    extras = mapOf(LocationEventTracker.Attribute.CLEANUP_STAGE to stage),
                )
            }
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
        isOnceLocationLatest = true
        isOnceLocation = false
        interval = intervalMs
        httpTimeOut = CONTINUOUS_HTTP_TIMEOUT_MILLIS
    }

    private fun buildSingleLocationOption(remainingMs: Long) = baseOption().apply {
        isOnceLocation = true
        isOnceLocationLatest = true
        // 两次采集共用总预算；不以默认 30 秒或新一轮 15 秒延长等待。
        httpTimeOut = remainingMs.coerceIn(1_000L, SingleLocationRequest.TIMEOUT_MS)
    }

    private fun AMapLocation.toSample(requestId: String = "") = LocationResult(
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
        errorCode = errorCode,
        errorInfo = errorInfo.orEmpty(),
        requestId = requestId,
    )

    /** Exactly one reporting subscriber; foreground actions borrow the same live SDK stream. */
    fun startContinuousLocation(owner: String): Flow<LocationResult> = flow {
        withContext(Dispatchers.Main.immediate) {
            synchronized(this@ContinuousAmapLocationManager) {
                if (trackingOwner != null && trackingOwner != owner) continuousClient?.let(::releaseContinuousClient)
                trackingOwner = owner
            }
        }
        try {
            ensureSampling()
            samples.collect { event ->
                if (event is LocationAcquisition.Success) emit(event.location)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                synchronized(this@ContinuousAmapLocationManager) {
                    if (trackingOwner == owner) {
                        trackingOwner = null
                        refreshOrRelease()
                    }
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun ensureSampling() = withContext(Dispatchers.Main.immediate) {
        currentCoroutineContext().ensureActive()
        val key = if (continuousClient == null) amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("amap_api_key_unavailable") else null
        currentCoroutineContext().ensureActive()
        synchronized(this@ContinuousAmapLocationManager) {
            if (continuousClient == null) {
                samples.resetReplayCache()
                lastEvent = null
                val request = initContinuousLocationClient(requireNotNull(key))
                val id = UUID.randomUUID().toString()
                lastCallbackElapsed = SystemClock.elapsedRealtime()
                val listener = AMapLocationListener { raw ->
                    if (!request.active.get()) return@AMapLocationListener
                    lastCallbackElapsed = SystemClock.elapsedRealtime()
                    if (raw == null) return@AMapLocationListener
                    val sample = raw.toSample(id)
                    if (raw.errorCode == 0) {
                        val event = LocationAcquisition.Success(sample)
                        lastEvent = event
                        samples.tryEmit(event)
                    } else {
                        val event = LocationAcquisition.Failure(locationFailure(raw.errorCode), sample)
                        lastEvent = event
                        samples.tryEmit(event)
                        LocationEventTracker.trackError(LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_ERROR,
                            extras = mapOf(LocationEventTracker.Attribute.ERROR_CODE to raw.errorCode))
                    }
                }
                request.listener = listener
                try {
                    request.client.setLocationListener(listener)
                    request.client.setLocationOption(buildContinuousLocationOption(desiredInterval()))
                    request.client.startLocation()
                } catch (error: Exception) {
                    releaseContinuousClient(request)
                    throw error
                }
            } else continuousClient?.client?.setLocationOption(buildContinuousLocationOption(desiredInterval()))
        }
    }

    private fun desiredInterval() = if (highRequested) MIN_INTERVAL else DEFAULT_INTERVAL

    private fun refreshOrRelease() {
        if (trackingOwner == null && !highRequested) continuousClient?.let(::releaseContinuousClient)
        else continuousClient?.client?.setLocationOption(buildContinuousLocationOption(desiredInterval()))
    }

    /** Called only by the Service's existing health loop, never by a background scheduler. */
    suspend fun recoverStalledSampling(owner: String): Boolean = withContext(Dispatchers.Main.immediate) {
        if (trackingOwner != owner || continuousClient == null ||
            SystemClock.elapsedRealtime() - lastCallbackElapsed < 90_000L) return@withContext false
        synchronized(this@ContinuousAmapLocationManager) { continuousClient?.let(::releaseContinuousClient) }
        ensureSampling()
        true
    }

    fun lastCallbackTime(): Long = lastCallbackElapsed

    private suspend fun acquireServiceOrderPoint(): LocationAcquisition {
        if (!serviceRequest.tryLock()) return LocationAcquisition.Failure(LocationFailure.BUSY)
        val actionId = UUID.randomUUID().toString()
        val notBefore = (System.currentTimeMillis() - 3_000L).coerceAtLeast(0L)
        var evidence: LocationAcquisition.Failure? = null
        try {
            return withTimeoutOrNull(30_000L) {
                withContext(Dispatchers.Main.immediate) { highRequested = true }
                ensureSampling()
                samples.first { event ->
                    when (event) {
                        is LocationAcquisition.Success -> {
                            val sample = event.location.copy(requestId = actionId)
                            val rejection = ServiceOrderLocationQuality.rejection(sample, System.currentTimeMillis(),
                                SystemClock.elapsedRealtime(), notBefore = notBefore)
                            if (rejection == null) true else {
                                evidence = LocationAcquisition.Failure(ServiceOrderLocationQuality.failure(sample, rejection), sample)
                                sample.isMock
                            }
                        }
                        is LocationAcquisition.Failure -> {
                            // 共享流的重放不能把上一动作的失败当成本次失败。
                            if (event.location?.receivedAt?.let { it < notBefore } == true) return@first false
                            evidence = event.copy(location = event.location?.copy(requestId = actionId))
                            event.reason == LocationFailure.PERMISSION || event.reason == LocationFailure.CONFIGURATION ||
                                event.location?.isMock == true ||
                                event.location?.errorCode == AMapLocation.ERROR_CODE_FAILURE_SIMULATION_LOCATION
                        }
                    }
                }.let { event ->
                    if (event is LocationAcquisition.Success && !event.location.isMock)
                        LocationAcquisition.Success(event.location.copy(requestId = actionId)) else requireNotNull(evidence)
                }
            } ?: evidence ?: LocationAcquisition.Failure(LocationFailure.TIMEOUT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            return LocationAcquisition.Failure(LocationFailure.PERMISSION)
        } catch (error: Exception) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL, error)
            return LocationAcquisition.Failure(LocationFailure.CONFIGURATION)
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    highRequested = false
                    refreshOrRelease()
                }
            } finally {
                serviceRequest.unlock()
            }
        }
    }

    suspend fun acquireCurrentLocation(serviceOrder: Boolean = false): LocationAcquisition {
        if (serviceOrder) return acquireServiceOrderPoint()
        val requestId = UUID.randomUUID().toString()
        return singleRequest.acquire(retryIf = { failure ->
            failure.location?.isMock != true &&
                failure.location?.errorCode != AMapLocation.ERROR_CODE_FAILURE_SIMULATION_LOCATION &&
                failure.reason == LocationFailure.QUALITY
        }) { remaining -> getIsolatedLocation(remaining, requestId) }
    }

    private suspend fun getIsolatedLocation(
        remainingMs: Long,
        requestId: String,
    ): LocationAcquisition = withContext(Dispatchers.Main.immediate) {
        val attemptStarted = System.nanoTime()
        val apiKey = amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() }
            ?: return@withContext LocationAcquisition.Failure(LocationFailure.CONFIGURATION)
        currentCoroutineContext().ensureActive()
        var client: AmapClientHandle? = null
        var listener: AMapLocationListener? = null
        try {
            val isolatedClient = clientFactory.create(apiKey)
            client = isolatedClient
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                listener = AMapLocationListener { location ->
                    if (finished.compareAndSet(false, true)) {
                        val result = if (location != null && location.errorCode == AMapLocation.LOCATION_SUCCESS) {
                            val sample = location.toSample(requestId)
                            val rejection = LocationQuality.rejection(sample, System.currentTimeMillis(), SystemClock.elapsedRealtime())
                            if (rejection == null) {
                                LocationAcquisition.Success(sample)
                            } else {
                                LocationAcquisition.Failure(LocationFailure.QUALITY, sample)
                            }
                        } else {
                            LocationAcquisition.Failure(locationFailure(location?.errorCode), location?.toSample(requestId))
                        }
                        continuation.resume(result)
                    }
                }
                continuation.invokeOnCancellation { finished.set(true) }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                isolatedClient.setLocationListener(requireNotNull(listener))
                val setupMs = (System.nanoTime() - attemptStarted) / 1_000_000
                isolatedClient.setLocationOption(buildSingleLocationOption(remainingMs - setupMs))
                isolatedClient.startLocation()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            LocationAcquisition.Failure(LocationFailure.PERMISSION)
        } catch (error: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                throwable = error,
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
                        throwable = it,
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
        val client = continuousClient?.client ?: return
        logI("权限变更，重启高德定位引擎")
        client.stopLocation()
        client.startLocation()
    }

    /**
     * 开启后台定位（绑定前台服务通知）
     * 解决锁屏后网络定位失败(Error 13)的问题
     *
     * @param notificationId 通知的ID
     * @param notification 通知对象
     */
    @Synchronized
    fun enableBackgroundLocation(notificationId: Int, notification: android.app.Notification, owner: String) {
        backgroundOwner = owner
        // 无论是否初始化，都缓存通知，确保重建时能自动恢复
        pendingNotification = notificationId to notification
        
        val request = continuousClient
        if (request == null) {
            logI("已缓存后台定位通知 (客户端尚未初始化)")
            return
        }
        try {
            request.backgroundEnabled = true
            request.client.enableBackgroundLocation(notificationId, notification)
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
     * 仅释放匹配会话；前台通知由 Android Service 统一移除。
     */
    @Synchronized
    fun disableBackgroundLocation(owner: String) {
        if (backgroundOwner != owner) return
        backgroundOwner = null
        pendingNotification = null
        val request = continuousClient ?: return
        try {
            request.client.disableBackgroundLocation(false)
            request.backgroundEnabled = false
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
