package com.ytone.longcare.features.location.manager

import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
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
        var lastAttemptedVisibility: Boolean? = null
        var firstVisibilityCheckPending = true
        var hasSuccessfulCallback = false
        var callbackCount = 0L
        var nullCallbackCount = 0L
        var lastCallbackAt: Long? = null
    }

    // SDK lifecycle and owner state are confined to Main.immediate.
    private var continuousClient: ContinuousClient? = null

    companion object {
        /** 默认定位间隔（毫秒） */
        private const val REPORTING_INTERVAL_MS = 30_000L
        /** 服务动作短时采集间隔（毫秒） */
        private const val ACTION_INTERVAL_MS = 3_000L
        private const val ACTION_WARM_SAMPLE_TOLERANCE_MS = 3_000L
        // 高德错误码 4 官方建议保留默认 30 秒；业务动作仍由自己的 30 秒总预算取消。
        private const val CONTINUOUS_HTTP_TIMEOUT_MILLIS = 30_000L
        private const val CALLBACK_STALL_TIMEOUT_MS = 90_000L
        private const val MAX_SDK_VERSION_LENGTH = 32
    }

    private val serviceRequest = Mutex()
    private val samples = MutableSharedFlow<LocationAcquisition>(replay = 1, extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var trackingOwner: String? = null
    // One automatic rebuild per owner's enabled sampling phase. Callbacks, actions and
    // same-owner recollection do not replenish it; only an explicit pause or a new owner can.
    private var recoveryAttemptedForOwner: String? = null
    private var samplingEnabled = true
    private var highRequested = false
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
                if (trackingOwner != owner && recoveryAttemptedForOwner != owner) recoveryAttemptedForOwner = null
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

    private suspend fun ensureSampling() = withContext(Dispatchers.Main.immediate) {
        currentCoroutineContext().ensureActive()
        if (trackingOwner != null && !samplingEnabled) return@withContext
        val owner = trackingOwner
        val key = if (continuousClient == null) amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("amap_api_key_unavailable") else null
        currentCoroutineContext().ensureActive()
        // Reading configuration may suspend; a pause or owner switch wins over the late key result.
        if (trackingOwner != owner || (trackingOwner != null && !samplingEnabled)) return@withContext
        if (continuousClient == null) startContinuousClient(requireNotNull(key))
        else continuousClient?.let {
            applySamplingOption(it)
            updateAppVisibility(it)
        }
    }

    /** All suspending configuration reads finish before replacing or starting an SDK client. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startContinuousClient(apiKey: String) {
        samples.resetReplayCache()
        val request = initContinuousLocationClient(apiKey)
        lastCallbackElapsed = SystemClock.elapsedRealtime()
        val listener = AMapLocationListener { raw ->
            if (!request.active.get()) return@AMapLocationListener
            // Count at the SDK boundary, before the shared flow can conflate a burst.
            request.callbackCount++
            request.lastCallbackAt = SystemClock.elapsedRealtime()
            if (raw == null) {
                request.nullCallbackCount++
                return@AMapLocationListener
            }
            // A callback proves liveness, not sustained recovery. In particular, a rebuilt
            // client's first point must not permit another rebuild after each silence.
            lastCallbackElapsed = SystemClock.elapsedRealtime()
            if (raw.errorCode == AMapLocation.LOCATION_SUCCESS && !request.hasSuccessfulCallback) {
                request.hasSuccessfulCallback = true
                // The SDK can discard notifications before asynchronous initialization completes.
                request.firstVisibilityCheckPending = !updateAppVisibility(request, force = true)
            }
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
            if (!request.hasSuccessfulCallback) updateAppVisibility(request)
        } catch (error: Exception) {
            releaseContinuousClient(request)
            throw error
        }
    }

    private fun desiredInterval() = if (highRequested) ACTION_INTERVAL_MS else REPORTING_INTERVAL_MS

    /** Reuses the Service health/visible-return check; never creates another lifecycle observer. */
    suspend fun syncAppVisibility(fromHealthTimer: Boolean = false) = withContext(Dispatchers.Main.immediate) {
        continuousClient?.let { request ->
            val initialCheck = fromHealthTimer && request.firstVisibilityCheckPending
            val attempted = updateAppVisibility(request, force = initialCheck)
            if (initialCheck && attempted) request.firstVisibilityCheckPending = false
        }
    }

    private fun updateAppVisibility(request: ContinuousClient, force: Boolean = false): Boolean {
        if (!request.active.get()) return false
        val visible = runtimeReadiness.isAppVisible() ?: return false
        if (!force && request.lastAttemptedVisibility == visible) return false
        // This SDK API adjusts signal listeners. Only one startup health compensation is allowed;
        // repeating the same state every tick would turn status synchronization into SDK retries.
        // An attempt is not an SDK acknowledgement. Even a throwing call must not start
        // unbounded same-state retries; startup compensation and actual transitions remain.
        request.lastAttemptedVisibility = visible
        runCatching { request.client.notifyAppEnterForeBack(visible) }
            .onFailure { logI("高德前后台状态同步失败，保留当前采集: ${it.javaClass.simpleName}") }
        return true
    }

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
        recoveryAttemptedForOwner = null
        continuousClient?.let(::releaseContinuousClient)
        samples.resetReplayCache()
        samples.tryEmit(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED))
    }

    /** At most once per owner's enabled phase, shared by Service health checks and actions. */
    suspend fun recoverStalledSampling(owner: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val stalledClient = continuousClient ?: return@withContext false
        if (!hasStalledSampling(owner) || recoveryAttemptedForOwner == owner) return@withContext false
        // Keep the current listener alive while configuration is read. Cancelling a foreground
        // action here must not leave the background collector with no SDK client.
        val key = amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("amap_api_key_unavailable")
        currentCoroutineContext().ensureActive()
        if (!hasStalledSampling(owner) || continuousClient !== stalledClient ||
            recoveryAttemptedForOwner == owner) return@withContext false
        recoveryAttemptedForOwner = owner
        releaseContinuousClient(stalledClient)
        try {
            startContinuousClient(key)
        } catch (error: Exception) {
            // A foreground action catches SDK startup errors for its UI. The reporting
            // collector must also receive the terminal failure instead of staying alive empty.
            samples.tryEmit(LocationAcquisition.Failure(
                if (error is SecurityException) LocationFailure.PERMISSION else LocationFailure.CONFIGURATION,
            ))
            throw error
        }
        true
    }

    suspend fun isSamplingStalled(owner: String): Boolean = withContext(Dispatchers.Main.immediate) {
        hasStalledSampling(owner)
    }

    private fun hasStalledSampling(owner: String): Boolean =
        trackingOwner == owner && samplingEnabled && continuousClient != null &&
            SystemClock.elapsedRealtime() - lastCallbackElapsed >= CALLBACK_STALL_TIMEOUT_MS

    /** Read only by bounded diagnostic reports; never changes SDK options or lifecycle. */
    fun diagnosticSnapshot(): Map<String, Any?> {
        val runtime = runtimeReadiness.diagnosticSnapshot()
        val request = continuousClient
        val now = SystemClock.elapsedRealtime()
        return linkedMapOf(
            "clientId" to request?.requestId,
            // Fixed labels keep the shared 30-field/950-character payload bounded.
            "runtime" to "idle=${runtime["deviceIdle"] ?: "?"},screen=${runtime["screenOn"] ?: "?"}," +
                "net=${runtime["networkAvailable"] ?: "?"},validated=${runtime["networkValidated"] ?: "?"}," +
                "exempt=${runtime["batteryExempt"] ?: "?"},save=${runtime["powerSave"] ?: "?"}",
            "sdkState" to request?.let {
                "count=${it.callbackCount},null=${it.nullCallbackCount}," +
                    "ageMs=${it.lastCallbackAt?.let { at -> (now - at).coerceAtLeast(0L) } ?: "?"}," +
                    "intervalMs=${it.appliedIntervalMs ?: "?"},visible=${it.lastAttemptedVisibility ?: "?"}," +
                    "recoveryUsed=${trackingOwner != null && recoveryAttemptedForOwner == trackingOwner}"
            },
        )
    }

    suspend fun acquireServiceOrderLocation(): LocationAcquisition {
        if (!serviceRequest.tryLock()) return LocationAcquisition.Failure(LocationFailure.BUSY)
        val actionId = UUID.randomUUID().toString()
        val notBefore = (System.currentTimeMillis() - ACTION_WARM_SAMPLE_TOLERANCE_MS).coerceAtLeast(0L)
        var evidence: LocationAcquisition.Failure? = null
        val startedAt = SystemClock.elapsedRealtime()
        val notBeforeElapsedRealtime = (startedAt - ACTION_WARM_SAMPLE_TOLERANCE_MS).coerceAtLeast(0L)
        val diagnosticUserId = runCatching { DiagnosticEventTracker.currentUserId() }.getOrDefault("0")
        var clientId: String? = null
        var warm = false
        var warmRejection: String? = null
        var lastRejection: String? = null
        var callbacks = 0
        var errors = 0
        var rejected = 0
        var outcome: LocationAcquisition? = null
        var cancelled = false
        var timedOut = false
        var failureType: String? = null
        var lastSdkErrorCode: Int? = null
        var clientRecovered = false
        var observedSample: com.ytone.longcare.model.LocationResult? = null
        try {
            val acquired = withTimeoutOrNull(30_000L) {
                val (previousEvent, warmSample) = withContext(Dispatchers.Main.immediate) {
                    val previous = latestEvent()
                    clientId = continuousClient?.requestId
                    observedSample = when (previous) {
                        is LocationAcquisition.Success -> previous.location
                        is LocationAcquisition.Failure -> previous.location
                        null -> null
                    }?.takeIf { it.requestId == clientId }
                    if (trackingOwner != null && !samplingEnabled) return@withContext previous to null
                    val warm = (previous as? LocationAcquisition.Success)?.location?.takeIf { sample ->
                        val rejection = ServiceOrderLocationQuality.rejection(
                            sample, System.currentTimeMillis(), SystemClock.elapsedRealtime(), notBefore = notBefore,
                            notBeforeElapsedRealtime = notBeforeElapsedRealtime,
                        )
                        warmRejection = rejection?.name
                        continuousClient != null && rejection == null
                    }
                    if (warm == null) highRequested = true
                    previous to warm
                }
                if (warmSample != null) {
                    warm = true
                    clientId = warmSample.requestId
                    return@withTimeoutOrNull LocationAcquisition.Success(warmSample.copy(requestId = actionId))
                }
                if (withContext(Dispatchers.Main.immediate) { trackingOwner != null && !samplingEnabled })
                    return@withTimeoutOrNull LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED)
                clientRecovered = withContext(Dispatchers.Main.immediate) {
                    trackingOwner?.let { recoverStalledSampling(it) } ?: false
                }
                ensureSampling()
                clientId = withContext(Dispatchers.Main.immediate) { continuousClient?.requestId }
                observedSample = observedSample?.takeIf { it.requestId == clientId }
                samples.first { event ->
                    // 旧回调只允许上面的合格热位置；旧失败或不合格点不作为新动作证据。
                    if (event === previousEvent) return@first false
                    callbacks++
                    observedSample = when (event) {
                        is LocationAcquisition.Success -> event.location
                        is LocationAcquisition.Failure -> event.location
                    } ?: observedSample
                    observedSample?.requestId?.takeIf { it.isNotBlank() }?.let { clientId = it }
                    when (event) {
                        is LocationAcquisition.Success -> {
                            val sample = event.location.copy(requestId = actionId)
                            val rejection = ServiceOrderLocationQuality.rejection(sample, System.currentTimeMillis(),
                                SystemClock.elapsedRealtime(), notBefore = notBefore,
                                notBeforeElapsedRealtime = notBeforeElapsedRealtime)
                            if (rejection == null) true else {
                                rejected++
                                lastRejection = rejection.name
                                evidence = LocationAcquisition.Failure(ServiceOrderLocationQuality.failure(sample, rejection), sample)
                                sample.isMock
                            }
                        }
                        is LocationAcquisition.Failure -> {
                            errors++
                            lastSdkErrorCode = event.location?.errorCode
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
            }
            timedOut = acquired == null
            outcome = acquired ?: evidence ?: LocationAcquisition.Failure(LocationFailure.TIMEOUT)
            return requireNotNull(outcome)
        } catch (error: CancellationException) {
            cancelled = true
            throw error
        } catch (_: SecurityException) {
            outcome = LocationAcquisition.Failure(LocationFailure.PERMISSION)
            return requireNotNull(outcome)
        } catch (error: Exception) {
            failureType = error.javaClass.simpleName
            outcome = LocationAcquisition.Failure(LocationFailure.CONFIGURATION)
            return requireNotNull(outcome)
        } finally {
            runCatching {
                val diagnosticNow = SystemClock.elapsedRealtime()
                val location = when (val result = outcome) {
                    is LocationAcquisition.Success -> result.location
                    is LocationAcquisition.Failure -> result.location
                    null -> null
                } ?: observedSample
                LocationEventTracker.trackEvent(
                    LocationEventTracker.EventType.SERVICE_ACTION_RESULT,
                    extras = linkedMapOf(
                        "actionId" to actionId, "clientId" to clientId,
                        "result" to if (cancelled) "cancelled" else when (val result = outcome) {
                            is LocationAcquisition.Success -> "success"
                            is LocationAcquisition.Failure -> result.reason.name
                            null -> "unknown"
                        },
                        "durationMs" to (diagnosticNow - startedAt).coerceAtLeast(0L),
                        "warm" to warm, "timedOut" to timedOut, "warmRejection" to warmRejection,
                        "qualityReason" to lastRejection, "callbackCount" to callbacks,
                        "errorCount" to errors, "rejectedCount" to rejected,
                        "errorCode" to lastSdkErrorCode, "failureType" to failureType, "accuracy" to location?.accuracy,
                        "locationType" to location?.locationType,
                        "sourceAgeMs" to location?.sourceElapsedRealtime?.takeIf { it > 0L && it <= diagnosticNow }
                            ?.let { diagnosticNow - it },
                        "clientRecovered" to clientRecovered,
                        "rawGapMs" to observedSample?.receivedElapsedRealtime?.takeIf { it > 0L }
                            ?.let { (diagnosticNow - it).coerceAtLeast(0L) },
                    ) + diagnosticSnapshot().filterKeys { it != "clientId" },
                    reportToServer = !cancelled,
                    userId = diagnosticUserId,
                )
            }

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
