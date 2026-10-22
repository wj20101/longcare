package com.ytone.longcare.features.location.manager

import android.net.ConnectivityManager
import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.diagnostics.locationTimeDiagnostic
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationQuality
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
        var staleSuccessSinceSourceProgress = false
    }

    // SDK lifecycle and owner state are confined to Main.immediate.
    private var continuousClient: ContinuousClient? = null

    companion object {
        /** 默认定位间隔（毫秒） */
        private const val REPORTING_INTERVAL_MS = 30_000L
        /** 服务动作短时采集间隔（毫秒） */
        private const val ACTION_INTERVAL_MS = 3_000L
        private const val ACTION_TIMEOUT_MS = 30_000L
        private const val ACTION_WARM_SAMPLE_TOLERANCE_MS = 3_000L
        // 高德错误码 4 官方建议保留默认 30 秒；业务动作仍由自己的 30 秒总预算取消。
        private const val CONTINUOUS_HTTP_TIMEOUT_MILLIS = 30_000L
        private const val BACKGROUND_SILENCE_REFRESH_MS = 45_000L
        private const val CALLBACK_SILENCE_DIAGNOSTIC_MS = 90_000L
        // A single callback from a restarted SDK does not replenish this per-enabled-phase budget.
        private const val MAX_RECOVERY_ATTEMPTS = 2
        private const val MAX_SDK_VERSION_LENGTH = 32
    }

    private val serviceRequest = Mutex()
    private val samples = MutableSharedFlow<LocationAcquisition>(replay = 1, extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var trackingOwner: String? = null
    private var samplingEnabled = true
    private var highRequested = false
    private var ownerGeneration = 0L
    private var samplingGeneration = 0L
    private var samplingStartedAt = 0L
    private var recoveryAttempts = 0
    private var lastRecoveryAt: Long? = null
    private var lastSourceProgressAt = 0L
    private var lastSourceAnchor: Long? = null
    private var lastEligibleSampleAt: Long? = null
    private var lastEligibleSourceAnchor: Long? = null
    private var lastRecoveryReason: String? = null
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
                val runtime = runtimeReadiness.diagnosticSnapshot()
                // Keep the SDK version and cause within the existing 30-field payload limit.
                mapOf("clientPurpose" to purpose, "backgroundPolicy" to backgroundPolicy(runtime)) +
                    runtime.filterKeys { it !in setOf("backgroundRestricted", "restrictBackgroundStatus", "lowPowerStandbyEnabled") }
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
        var generation = -1L
        try {
            withContext(Dispatchers.Main.immediate) {
                if (trackingOwner != null && trackingOwner != owner) continuousClient?.let(::releaseContinuousClient)
                generation = ++ownerGeneration
                resetSamplingHealth()
                trackingOwner = owner
                samplingEnabled = initiallyEnabled
            }
            if (initiallyEnabled) ensureSampling()
            else withContext(Dispatchers.Main.immediate) { pauseSampling() }
            samples.collect { emit(it) }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (trackingOwner == owner && ownerGeneration == generation) {
                    resetSamplingHealth()
                    trackingOwner = null
                    samplingEnabled = true
                    refreshOrRelease()
                }
            }
        }
    }

    private suspend fun ensureSampling(shouldContinue: () -> Boolean = { true }) = withContext(Dispatchers.Main.immediate) {
        currentCoroutineContext().ensureActive()
        if (!shouldContinue()) return@withContext
        if (trackingOwner != null && !samplingEnabled) return@withContext
        val owner = trackingOwner
        val generation = samplingGeneration
        val keyResult = if (continuousClient == null) runCatching { amapApiKeyProvider.getAmapApiKey() } else null
        currentCoroutineContext().ensureActive()
        keyResult?.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (!shouldContinue()) return@withContext
        // Reading configuration may suspend; a pause or owner switch wins over the late key result.
        if (trackingOwner != owner || samplingGeneration != generation ||
            (trackingOwner != null && !samplingEnabled)) return@withContext
        if (continuousClient == null) startContinuousClient(keyResult?.getOrThrow()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("amap_api_key_unavailable"))
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
        val startedAt = SystemClock.elapsedRealtime()
        lastCallbackElapsed = startedAt
        val listener = AMapLocationListener { raw ->
            if (!request.active.get()) return@AMapLocationListener
            // Count at the SDK boundary, before the shared flow can conflate a burst.
            request.callbackCount++
            request.lastCallbackAt = SystemClock.elapsedRealtime()
            if (raw == null) {
                request.nullCallbackCount++
                return@AMapLocationListener
            }
            // 非空回调表示 SDK 仍在返回结果，质量和业务上报资格由消费方判断。
            val callbackAt = SystemClock.elapsedRealtime()
            lastCallbackElapsed = callbackAt
            if (raw.errorCode == AMapLocation.LOCATION_SUCCESS && !request.hasSuccessfulCallback) {
                request.hasSuccessfulCallback = true
                // The SDK can discard notifications before asynchronous initialization completes.
                request.firstVisibilityCheckPending = !updateAppVisibility(request, force = true)
            }
            val sample = raw.toSample(request.requestId)
            if (raw.errorCode == AMapLocation.LOCATION_SUCCESS) {
                // Source liveness is not business eligibility: a trustworthy new fix with >200m
                // accuracy proves SDK progress, but still cannot qualify for an action or upload.
                val sourceRejection = LocationQuality.rejection(sample, sample.receivedAt, sample.receivedElapsedRealtime,
                    maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS, maxAccuracyMeters = null, allowNormalTrust = true,
                    notBeforeElapsedRealtime = samplingStartedAt.takeIf { trackingOwner != null })
                val source = LocationQuality.sampleElapsedRealtime(sample)
                val advances = lastSourceAnchor?.let { source > it } ?: true
                if (sourceRejection == null && advances) {
                    lastSourceProgressAt = sample.receivedElapsedRealtime
                    lastSourceAnchor = source
                    request.staleSuccessSinceSourceProgress = false
                } else {
                    // Errors and invalid metadata do not clear prior stale evidence. Repeated
                    // trustworthy sources remain unproductive even when their accuracy is poor.
                    request.staleSuccessSinceSourceProgress = request.staleSuccessSinceSourceProgress ||
                        sourceRejection == LocationQuality.Rejection.STALE || sourceRejection == LocationQuality.Rejection.ORDER ||
                        (sourceRejection == null && !advances)
                }
                val eligible = ServiceOrderLocationQuality.rejection(sample, sample.receivedAt, sample.receivedElapsedRealtime,
                    maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
                    notBeforeElapsedRealtime = samplingStartedAt.takeIf { trackingOwner != null }) == null
                if (eligible && (lastEligibleSourceAnchor?.let { source > it } ?: true)) {
                    lastEligibleSampleAt = sample.receivedElapsedRealtime
                    lastEligibleSourceAnchor = source
                }
            }
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
            if (!request.active.get() || continuousClient !== request) return
            applySamplingOption(request)
            if (!request.active.get() || continuousClient !== request) return
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
            if (fromHealthTimer) recoverUnproductiveSampling(request)
        }
    }

    private fun resetSamplingHealth() {
        samplingGeneration++
        samplingStartedAt = SystemClock.elapsedRealtime()
        recoveryAttempts = 0
        lastRecoveryAt = null
        lastSourceProgressAt = samplingStartedAt
        lastSourceAnchor = null
        lastEligibleSampleAt = null
        lastEligibleSourceAnchor = null
        lastRecoveryReason = null
    }

    private fun samplingRecoveryReason(request: ContinuousClient, now: Long): String? {
        if (!request.active.get() || continuousClient !== request || trackingOwner == null ||
            !samplingEnabled || highRequested || recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) return null
        if (lastRecoveryAt?.let { now - it < BACKGROUND_SILENCE_REFRESH_MS } == true) return null
        if (request.staleSuccessSinceSourceProgress && now - lastSourceProgressAt >= CALLBACK_SILENCE_DIAGNOSTIC_MS)
            return "s"
        val silence = now - lastCallbackElapsed
        val backgroundOrScreenOff = runtimeReadiness.isAppVisible() == false ||
            runtimeReadiness.diagnosticSnapshot()["screenOn"] == false
        return if (silence >= CALLBACK_SILENCE_DIAGNOSTIC_MS ||
            (backgroundOrScreenOff && silence >= BACKGROUND_SILENCE_REFRESH_MS)) "c" else null
    }

    private suspend fun recoverUnproductiveSampling(request: ContinuousClient) {
        if (samplingRecoveryReason(request, SystemClock.elapsedRealtime()) == null) return
        val owner = trackingOwner
        val generation = samplingGeneration
        // Keep the existing listener alive while configuration can suspend. Fresh progress, pause,
        // cancellation or an owner generation change must win over a late configuration response.
        val keyResult = runCatching { amapApiKeyProvider.getAmapApiKey() }
        currentCoroutineContext().ensureActive()
        keyResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (trackingOwner != owner || samplingGeneration != generation) return
        val recoveryAt = SystemClock.elapsedRealtime()
        val reason = samplingRecoveryReason(request, recoveryAt) ?: return
        val key = keyResult.getOrThrow()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("amap_api_key_unavailable")
        lastRecoveryAt = recoveryAt
        lastRecoveryReason = reason
        releaseContinuousClient(request)
        // Never stop/set/start a logically active instance: invalidate before stopping so delayed
        // old callbacks cannot revive it. Startup failure also clears the replacement instance.
        while (recoveryAttempts < MAX_RECOVERY_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            if (trackingOwner != owner || samplingGeneration != generation || !samplingEnabled) return
            recoveryAttempts++
            try {
                startContinuousClient(key)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (trackingOwner != owner || samplingGeneration != generation || !samplingEnabled) return
                if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) throw error
                // One bounded replacement retry; exhaustion propagates to the Service's interruption path.
                logI("高德恢复客户端启动失败，执行最后一次重建: ${error.javaClass.simpleName}")
            }
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
            resetSamplingHealth()
            if (enabled) ensureSampling() else pauseSampling()
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun pauseSampling() {
        continuousClient?.let(::releaseContinuousClient)
        samples.resetReplayCache()
        samples.tryEmit(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED))
    }

    /** Reporting remains degraded after the replacement budget is exhausted; no false healthy state. */
    suspend fun isSamplingStalled(owner: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val request = continuousClient
        val now = SystemClock.elapsedRealtime()
        trackingOwner == owner && samplingEnabled && request != null &&
            (now - lastCallbackElapsed >= CALLBACK_SILENCE_DIAGNOSTIC_MS ||
                (request.staleSuccessSinceSourceProgress && now - lastSourceProgressAt >= CALLBACK_SILENCE_DIAGNOSTIC_MS))
    }

    /** Read only by bounded diagnostic reports; never changes SDK options or lifecycle. */
    fun diagnosticSnapshot(): Map<String, Any?> {
        val runtime = runtimeReadiness.diagnosticSnapshot()
        val request = continuousClient
        val now = SystemClock.elapsedRealtime()
        return linkedMapOf(
            "clientId" to request?.requestId,
            // idle is deep/light; unsupported or unreadable state stays ?, never a guessed 0.
            // Fixed labels keep the shared 30-field/950-character payload bounded.
            "runtime" to "idle=${bit(runtime, "deviceIdle")}/${bit(runtime, "deviceLightIdle")},screen=${bit(runtime, "screenOn")}," +
                "net=${bit(runtime, "networkAvailable")},validated=${bit(runtime, "networkValidated")}," +
                "exempt=${bit(runtime, "batteryExempt")},save=${bit(runtime, "powerSave")},${backgroundPolicy(runtime)}",
            "sdkState" to request?.let {
                "count=${boundedMetric(it.callbackCount)},null=${boundedMetric(it.nullCallbackCount)}," +
                    "ageMs=${progressGap(it.lastCallbackAt, now)}," +
                    "intervalMs=${it.appliedIntervalMs ?: "?"},visible=${it.lastAttemptedVisibility ?: "?"}," +
                    "gS=${progressGapSeconds(lastSourceProgressAt.takeIf { lastSourceAnchor != null }, now)}/${progressGapSeconds(lastEligibleSampleAt, now)}," +
                    "r=${lastRecoveryReason ?: "n"}:$recoveryAttempts/$MAX_RECOVERY_ATTEMPTS"
            },
        )
    }

    // gS is source/eligible observation gaps in seconds, not fix age or HTTP success. Unknown is ?;
    // + denotes a saturated lower bound. r is the last attempted trigger (n=none/s=source/c=callback)
    // and used/max attempts. Short, bounded values also preserve the shared 950-character budget.
    private fun progressGap(at: Long?, now: Long): String = at?.let { boundedMetric((now - it).coerceAtLeast(0L)) } ?: "?"

    private fun progressGapSeconds(at: Long?, now: Long): String = at?.let {
        val seconds = (now - it).coerceAtLeast(0L) / 1_000L
        if (seconds >= 999_999L) "999999+" else seconds.toString()
    } ?: "?"

    private fun boundedMetric(value: Long): String = if (value > 999_999_999L) "999999999+" else value.toString()

    private fun bit(runtime: Map<String, Any?>, key: String): String =
        when (runtime[key]) { true -> "1"; false -> "0"; else -> "?" }

    private fun backgroundPolicy(runtime: Map<String, Any?>): String {
        val dataSaver = when (val status = runtime["restrictBackgroundStatus"]) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED,
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> status.toString()
            else -> "?"
        }
        return "bg=${bit(runtime, "backgroundRestricted")},ds=$dataSaver,lps=${bit(runtime, "lowPowerStandbyEnabled")}"
    }

    suspend fun acquireServiceOrderLocation(): LocationAcquisition {
        if (!serviceRequest.tryLock()) return LocationAcquisition.Failure(LocationFailure.BUSY)
        val actionId = UUID.randomUUID().toString()
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
        var observedSample: com.ytone.longcare.model.LocationResult? = null
        // Main's coroutine timeout uses Handler uptime, which stops during deep sleep. Keep an
        // elapsed-realtime deadline as the acceptance boundary without changing sample quality.
        fun deadlineReached() = SystemClock.elapsedRealtime() - startedAt >= ACTION_TIMEOUT_MS
        try {
            val acquired = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
                val (previousEvent, warmSample) = withContext(Dispatchers.Main.immediate) {
                    val previous = latestEvent()
                    clientId = continuousClient?.requestId
                    observedSample = when (previous) {
                        is LocationAcquisition.Success -> previous.location
                        is LocationAcquisition.Failure -> previous.location
                        null -> null
                    }?.takeIf { it.requestId == clientId }
                    if (deadlineReached()) return@withContext previous to null
                    if (trackingOwner != null && !samplingEnabled) return@withContext previous to null
                    val warm = (previous as? LocationAcquisition.Success)?.location?.takeIf { sample ->
                        val rejection = ServiceOrderLocationQuality.rejection(
                            sample, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                            notBeforeElapsedRealtime = notBeforeElapsedRealtime,
                        )
                        warmRejection = rejection?.name
                        continuousClient != null && rejection == null
                    }
                    if (warm == null) highRequested = true
                    previous to warm
                }
                if (deadlineReached()) return@withTimeoutOrNull null
                if (warmSample != null) {
                    warm = true
                    clientId = warmSample.requestId
                    return@withTimeoutOrNull LocationAcquisition.Success(warmSample.copy(requestId = actionId))
                }
                if (withContext(Dispatchers.Main.immediate) { trackingOwner != null && !samplingEnabled })
                    return@withTimeoutOrNull LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED)
                ensureSampling { !deadlineReached() }
                if (deadlineReached()) return@withTimeoutOrNull null
                clientId = withContext(Dispatchers.Main.immediate) { continuousClient?.requestId }
                if (deadlineReached()) return@withTimeoutOrNull null
                observedSample = observedSample?.takeIf { it.requestId == clientId }
                samples.first { event ->
                    // A late callback terminates waiting, but cannot replace in-budget evidence.
                    if (deadlineReached()) return@first true
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
                                SystemClock.elapsedRealtime(),
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
                    if (deadlineReached()) null
                    else if (event is LocationAcquisition.Success && !event.location.isMock)
                        LocationAcquisition.Success(event.location.copy(requestId = actionId)) else requireNotNull(evidence)
                }
            }
            timedOut = acquired == null || (acquired is LocationAcquisition.Success && deadlineReached())
            outcome = acquired.takeUnless { timedOut } ?: evidence ?: LocationAcquisition.Failure(LocationFailure.TIMEOUT)
        } catch (error: CancellationException) {
            cancelled = true
            throw error
        } catch (_: SecurityException) {
            outcome = LocationAcquisition.Failure(LocationFailure.PERMISSION)
        } catch (error: Exception) {
            failureType = error.javaClass.simpleName
            outcome = LocationAcquisition.Failure(LocationFailure.CONFIGURATION)
        } finally {
            // Retain this client's state before standalone cleanup releases it. Cleanup can itself
            // resume after sleep, so settle the final deadline and outcome before logging/returning.
            val closingSnapshot = runCatching { diagnosticSnapshot() }.getOrDefault(emptyMap())
            try {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    highRequested = false
                    refreshOrRelease()
                }
            } finally {
                if (!cancelled && outcome is LocationAcquisition.Success && deadlineReached()) {
                    timedOut = true
                    outcome = evidence ?: LocationAcquisition.Failure(LocationFailure.TIMEOUT)
                }
                runCatching {
                    val diagnosticNow = SystemClock.elapsedRealtime()
                    val diagnosticWallNow = System.currentTimeMillis()
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
                            "time" to location?.let { locationTimeDiagnostic(
                                it, diagnosticWallNow, diagnosticNow,
                                LocationQuality.timeIssue(it, diagnosticWallNow, diagnosticNow)?.name,
                            ) },
                            "rawGapMs" to observedSample?.receivedElapsedRealtime?.takeIf { it > 0L }
                                ?.let { (diagnosticNow - it).coerceAtLeast(0L) },
                        ) + closingSnapshot.filterKeys { it != "clientId" },
                        reportToServer = !cancelled,
                        userId = diagnosticUserId,
                    )
                }

                serviceRequest.unlock()
            }
        }
        return requireNotNull(outcome)
    }
}
