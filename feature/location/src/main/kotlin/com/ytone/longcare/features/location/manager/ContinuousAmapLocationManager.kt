package com.ytone.longcare.features.location.manager

import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.features.location.core.AndroidLocationRuntimeReadiness
import com.ytone.longcare.features.location.tracker.ContinuousLocationDiagnostics
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 签到、签退借用服务中的同一采集流；销售、拍照仍使用独立单次请求。 */
@Singleton
class ContinuousAmapLocationManager @Inject constructor(
    private val clientFactory: AmapLocationClientFactory,
    private val amapApiKeyProvider: AmapApiKeyProvider,
    private val runtimeReadiness: AndroidLocationRuntimeReadiness,
) {
    private class ContinuousClient(val client: AmapClientHandle, runtimeSnapshot: () -> Map<String, Any?>) {
        val active = AtomicBoolean(true)
        val requestId: String = UUID.randomUUID().toString()
        val diagnostics = ContinuousLocationDiagnostics(requestId, CONTINUOUS_HTTP_TIMEOUT_MILLIS,
            runtimeSnapshot = {
                runtimeSnapshot() + ("amapSdkVersion" to runCatching { client.sdkVersion() }
                    .getOrNull()?.takeIf { it.isNotBlank() }?.take(MAX_SDK_VERSION_LENGTH))
            },
        )
        var listener: AMapLocationListener? = null
        var appliedIntervalMs: Long? = null
    }

    // SDK lifecycle and owner state are confined to Main.immediate.
    private var continuousClient: ContinuousClient? = null

    companion object {
        /** 默认定位间隔（毫秒） */
        private const val REPORTING_INTERVAL_MS = 30_000L
        /** 服务动作短时采集间隔（毫秒） */
        private const val ACTION_INTERVAL_MS = 3_000L
        // 高德错误码 4 官方建议保留默认 30 秒；业务动作仍由自己的 30 秒总预算取消。
        private const val CONTINUOUS_HTTP_TIMEOUT_MILLIS = 30_000L
        private const val MAX_SDK_VERSION_LENGTH = 32
    }

    private val serviceRequest = Mutex()
    private val samples = MutableSharedFlow<LocationAcquisition>(replay = 1, extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var trackingOwner: String? = null
    private var samplingEnabled = true
    private var highRequested = false
    private val receivedCallbacks = AtomicLong()
    @Volatile private var lastCallbackElapsed = 0L
    fun latestEvent(): LocationAcquisition? = samples.replayCache.lastOrNull()

    /** 每次收集创建独立客户端，避免旧订单的在途 SDK 结果进入新监听器。 */
    private fun initContinuousLocationClient(
        apiKey: String,
    ): ContinuousClient {
        check(continuousClient == null) { "amap_continuous_collection_already_active" }
        var created: ContinuousClient? = null
        try {
            val purpose = if (trackingOwner != null) "order_reporting" else "service_action"
            val request = ContinuousClient(clientFactory.create(apiKey)) {
                mapOf("clientPurpose" to purpose) + runtimeReadiness.diagnosticSnapshot()
            }
            created = request
            continuousClient = request
            logI("持续高德定位客户端初始化成功，间隔: ${desiredInterval()}ms")

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

    private fun releaseContinuousClient(request: ContinuousClient) {
        if (!request.active.compareAndSet(true, false)) return
        if (continuousClient === request) continuousClient = null
        request.diagnostics.finish()
        // 仅释放本次客户端；任一步失败仍执行后续释放。
        val client = request.client
        listOf<Pair<String, () -> Unit>>(
            "unregister_listener" to { request.listener?.let(client::unRegisterLocationListener) },
            "stop_location" to { client.stopLocation() },
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

    private fun buildContinuousLocationOption(intervalMs: Long) = newAmapLocationOption().apply {
        // SDK 3.7+ 支持持续定位；仅首次等待 Wi-Fi 刷新，约增加 1～3 秒。
        isOnceLocationLatest = true
        isOnceLocation = false
        interval = intervalMs
        httpTimeOut = CONTINUOUS_HTTP_TIMEOUT_MILLIS
    }

    /** Exactly one reporting subscriber; foreground actions borrow the same live SDK stream. */
    fun startContinuousLocation(owner: String, initiallyEnabled: Boolean = true): Flow<LocationAcquisition> = flow {
        try {
            withContext(Dispatchers.Main.immediate) {
                if (trackingOwner != null && trackingOwner != owner) continuousClient?.let(::releaseContinuousClient)
                trackingOwner = owner
                samplingEnabled = initiallyEnabled
            }
            if (initiallyEnabled) ensureSampling()
            else withContext(Dispatchers.Main.immediate) { pauseSampling() }
            samples.collect { emit(it) }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (trackingOwner == owner) {
                    trackingOwner = null
                    samplingEnabled = true
                    refreshOrRelease()
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun ensureSampling() = withContext(Dispatchers.Main.immediate) {
        currentCoroutineContext().ensureActive()
        if (trackingOwner != null && !samplingEnabled) return@withContext
        val owner = trackingOwner
        val key = if (continuousClient == null) amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("amap_api_key_unavailable") else null
        currentCoroutineContext().ensureActive()
        // Reading configuration may suspend; a pause or owner switch wins over the late key result.
        if (trackingOwner != owner || (trackingOwner != null && !samplingEnabled)) return@withContext
        if (continuousClient == null) {
            samples.resetReplayCache()
            val request = initContinuousLocationClient(requireNotNull(key))
            lastCallbackElapsed = SystemClock.elapsedRealtime()
            val listener = AMapLocationListener { raw ->
                if (!request.active.get()) return@AMapLocationListener
                if (raw == null) return@AMapLocationListener
                // Sequence distinguishes a real callback from client startup in the same clock tick.
                receivedCallbacks.incrementAndGet()
                lastCallbackElapsed = SystemClock.elapsedRealtime()
                val sample = raw.toSample(request.requestId)
                val event = if (raw.errorCode == AMapLocation.LOCATION_SUCCESS) LocationAcquisition.Success(sample) else {
                    LocationAcquisition.Failure(locationFailure(raw.errorCode), sample)
                }
                // Main.immediate consumers can finish the action and release this client inside tryEmit.
                // Settle this callback's diagnostics before that synchronous cleanup marks them finished.
                request.diagnostics.onLocation(raw, request.appliedIntervalMs ?: desiredInterval())
                samples.tryEmit(event)
            }
            request.listener = listener
            try {
                request.client.setLocationListener(listener)
                applySamplingOption(request)
                request.client.startLocation()
            } catch (error: Exception) {
                releaseContinuousClient(request)
                throw error
            }
        } else continuousClient?.let(::applySamplingOption)
    }

    private fun desiredInterval() = if (highRequested) ACTION_INTERVAL_MS else REPORTING_INTERVAL_MS

    private fun applySamplingOption(request: ContinuousClient) {
        val interval = desiredInterval()
        if (request.appliedIntervalMs == interval) return
        // 当前 SDK 在运行中应用 option 会内部重启采集；相同配置不重复下发。
        request.client.setLocationOption(buildContinuousLocationOption(interval))
        request.appliedIntervalMs = interval
    }

    private fun refreshOrRelease() {
        if ((trackingOwner != null && !samplingEnabled) || (trackingOwner == null && !highRequested)) continuousClient?.let(::releaseContinuousClient)
        else continuousClient?.let(::applySamplingOption)
    }

    /** Pausing retains the reporting subscription and owner, but never reuses a pre-pause point. */
    suspend fun setSamplingEnabled(owner: String, enabled: Boolean) =
        withContext(Dispatchers.Main.immediate) {
            if (trackingOwner != owner || samplingEnabled == enabled) return@withContext
            samplingEnabled = enabled
            if (enabled) ensureSampling() else pauseSampling()
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun pauseSampling() {
        continuousClient?.let(::releaseContinuousClient)
        samples.resetReplayCache()
        samples.tryEmit(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED))
    }

    /** Called only by the Service's existing health loop, never by a background scheduler. */
    suspend fun recoverStalledSampling(owner: String): Boolean = withContext(Dispatchers.Main.immediate) {
        if (trackingOwner != owner || !samplingEnabled || continuousClient == null ||
            SystemClock.elapsedRealtime() - lastCallbackElapsed < 90_000L) return@withContext false
        continuousClient?.let(::releaseContinuousClient)
        ensureSampling()
        true
    }

    fun lastCallbackTime(): Long = lastCallbackElapsed

    fun callbackSequence(): Long = receivedCallbacks.get()

    suspend fun acquireServiceOrderLocation(): LocationAcquisition {
        if (!serviceRequest.tryLock()) return LocationAcquisition.Failure(LocationFailure.BUSY)
        val actionId = UUID.randomUUID().toString()
        val notBefore = (System.currentTimeMillis() - 3_000L).coerceAtLeast(0L)
        var evidence: LocationAcquisition.Failure? = null
        try {
            return withTimeoutOrNull(30_000L) {
                val (previousEvent, warmSample) = withContext(Dispatchers.Main.immediate) {
                    val previous = latestEvent()
                    if (trackingOwner != null && !samplingEnabled) return@withContext previous to null
                    val warm = (previous as? LocationAcquisition.Success)?.location?.takeIf { sample ->
                        continuousClient != null && ServiceOrderLocationQuality.rejection(
                            sample, System.currentTimeMillis(), SystemClock.elapsedRealtime(), notBefore = notBefore,
                        ) == null
                    }
                    if (warm == null) highRequested = true
                    previous to warm
                }
                if (warmSample != null) return@withTimeoutOrNull LocationAcquisition.Success(warmSample.copy(requestId = actionId))
                if (withContext(Dispatchers.Main.immediate) { trackingOwner != null && !samplingEnabled })
                    return@withTimeoutOrNull LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED)
                ensureSampling()
                samples.first { event ->
                    // 旧回调只允许上面的合格热位置；旧失败或不合格点不作为新动作证据。
                    if (event === previousEvent) return@first false
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
                            evidence = event.copy(location = event.location?.copy(requestId = actionId))
                            event.reason == LocationFailure.PERMISSION || event.reason == LocationFailure.CONFIGURATION ||
                                event.reason == LocationFailure.SERVICE_DISABLED ||
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
}
