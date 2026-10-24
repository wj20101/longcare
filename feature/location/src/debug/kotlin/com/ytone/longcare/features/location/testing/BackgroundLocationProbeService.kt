package com.ytone.longcare.features.location.testing

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.location.ILogCallback
import com.ytone.longcare.domain.location.*
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.location.core.LocationForegroundServiceController
import com.ytone.longcare.features.location.core.AndroidLocationRuntimeReadiness
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.manager.AmapClientHandle
import com.ytone.longcare.features.location.manager.AmapLocationClientFactory
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import com.ytone.longcare.features.location.manager.SdkAmapClientHandle
import com.ytone.longcare.features.location.manager.SerializedAmapClientProvider
import com.ytone.longcare.feature.location.R
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.features.location.service.LocationTrackingService
import com.ytone.longcare.features.location.session.LocationSessionLifecycleObserver
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import java.lang.reflect.Proxy
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Debug-only native lifecycle fixture. Executes the production Service and reporting logic,
 * while order state and uploads stay local. Never touches the real account or care backend.
 * Use realSource=true for real AMap samples; deterministic samples only verify lifecycle.
 */
class BackgroundLocationProbeService : LocationTrackingService() {
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val identity = LocationAccount(901, 902, 903)
    private lateinit var sessionState: MutableStateFlow<SessionState>
    private lateinit var trackingManager: LocationTrackingManager
    private var traceClientId: Int? = null
    private val traceFile by lazy { File(filesDir, "background-location-probe.jsonl") }
    private val traceHandler = Handler(Looper.getMainLooper())
    private val sdkLogSessions = mutableSetOf<SdkLogSession>()
    private var activeOrderId: Long
        get() = prefs.getLong("orderId", ORDER_ID)
        set(value) { prefs.edit().putLong("orderId", value).commit() }

    override fun onCreate() {
        super.onCreate()
        prefs.edit().putInt("boots", prefs.getInt("boots", 0) + 1).commit()
        trace("service_create")
        val recordStore = object : LocationRecoveryStore {
            override suspend fun read(): LocationRecoveryRecord? = prefs.getString("session", null)?.let {
                LocationRecoveryRecord(probeAccount(), activeOrderId, it)
            }
            override suspend fun save(record: LocationRecoveryRecord) {
                prefs.edit().putString("session", record.sessionId).putLong("orderId", record.orderId)
                    .putInt("userId", record.account.userId).commit()
                trace("recovery_save", "orderId" to record.orderId)
            }
            override suspend fun clear(sessionId: String?) {
                if (sessionId == null || prefs.getString("session", null) == sessionId) {
                    prefs.edit().remove("session").commit()
                    trace("recovery_clear")
                }
            }
        }
        recoveryStore = recordStore
        sessionState = MutableStateFlow(loggedIn(probeAccount()))
        userSessionRepository = object : UserSessionRepository {
            override val sessionState = this@BackgroundLocationProbeService.sessionState
            // This UI/probe fake never authenticates real network requests.
            override val sessionSnapshot = kotlinx.coroutines.flow.MutableStateFlow(com.ytone.longcare.model.UserSessionSnapshot(0L, null))
            override suspend fun logoutIfCurrent(generation: Long): Long? = null
            override suspend fun login(user: User) = error("probe does not change account")
            override suspend fun updateUser(user: User) = error("probe does not change account")
            override suspend fun logout() = error("probe does not change account")
        }
        val orders = Proxy.newProxyInstance(OrderRepository::class.java.classLoader,
            arrayOf(OrderRepository::class.java)) { _, method, args ->
            check(method.name == "getOrderState") { "probe does not call business APIs" }
            val orderId = args[0] as Long
            trace("order_query", "orderId" to orderId)
            ApiResult.Success(ServiceOrderStateModel(orderId,
                if (prefs.getBoolean("orderEnded", false)) ServiceOrderStateModel.STATE_COMPLETED
                else ServiceOrderStateModel.STATE_IN_PROGRESS))
        } as OrderRepository
        val platformReadiness = runtimeReadiness
        runtimeReadiness = object : LocationRuntimeReadiness {
            // Optional local gates exercise the app response; actual Android permission still wins.
            override fun hasLocationPermission() = platformReadiness.hasLocationPermission() && prefs.getBoolean("permissionGranted", true)
            override fun isLocationServiceEnabled() = platformReadiness.isLocationServiceEnabled() && prefs.getBoolean("locationEnabled", true)
        }
        val context = object : ContextWrapper(this) {
            private fun redirect(intent: Intent) = Intent(intent).setClass(baseContext, BackgroundLocationProbeService::class.java)
            override fun startForegroundService(service: Intent): ComponentName? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) baseContext.startForegroundService(redirect(service))
                else baseContext.startService(redirect(service))
            override fun startService(service: Intent): ComponentName? = baseContext.startService(redirect(service))
            override fun stopService(service: Intent): Boolean = baseContext.stopService(redirect(service))
        }
        keepAliveManager = LocationKeepAliveManager(LocationForegroundServiceController(context, runtimeReadiness))
        reportingManager = LocationReportingManager(keepAliveManager, orders, probeScope, recordStore)
        trackingManager = LocationTrackingManager(reportingManager)
        LocationSessionLifecycleObserver(probeScope, userSessionRepository, trackingManager).start()
        locationRepository = object : LocationRepository {
            override suspend fun addPosition(orderId: Long, latitude: Double, longitude: Double): ApiResult<Unit> {
                check(orderId == ORDER_ID || orderId == SECOND_ORDER_ID)
                val request = prefs.getInt("httpRequests", 0) + 1
                prefs.edit().putInt("httpRequests", request).commit()
                trace("http_start", "request" to request, "orderId" to orderId)
                var outcome = "cancelled"
                try {
                    if (prefs.getBoolean("httpSuspend", false)) awaitCancellation()
                    delay(prefs.getLong("httpDelayMs", 0L))
                    if (prefs.getBoolean("httpFail", false)) {
                        outcome = "failure"
                        return ApiResult.Exception(IOException("isolated_probe_network_failure"))
                    }
                    // Counters only: never persist coordinates, accounts or care order records.
                    prefs.edit().putInt("uploads", prefs.getInt("uploads", 0) + 1)
                        .putLong("lastUpload", System.currentTimeMillis()).commit()
                    outcome = "success"
                    return ApiResult.Success(Unit)
                } finally {
                    trace("http_end", "request" to request, "orderId" to orderId, "outcome" to outcome)
                }
            }
        }
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, ProbeLocationDependencies::class.java)
        // Keep the production sampler and its account guard, with the same isolated business
        // identity used by this service's order lifecycle and local receiver.
        val sharedProvider = SerializedAmapClientProvider(dependencies.amapClientFactory(), userSessionRepository)
        val factory = object : AmapLocationClientFactory(this, privacyConsentManager) {
            override fun create(apiKey: String): AmapClientHandle {
                val clientId = prefs.getInt("clients", 0) + 1
                prefs.edit().putInt("clients", clientId).commit()
                val realSource = prefs.getBoolean("realSource", false)
                val rawSdkClient = prefs.getBoolean("rawSdkClient", false)
                val client = when {
                    !realSource -> ProbeClient()
                    rawSdkClient -> dependencies.amapClientFactory().create(apiKey)
                    else -> sharedProvider.create(apiKey)
                }
                traceClientId = clientId
                trace("client_create", "client" to clientId, "source" to if (realSource) "amap" else "fake",
                    "clientKind" to if (!realSource) "fake" else if (rawSdkClient) "raw_sdk" else "shared_provider")
                // SDK log registration is process-global; opt-in applies only at client creation.
                return TracedClient(client, clientId, prefs.getBoolean("sdkLogs", false),
                    nativeSdkBoundary = realSource && rawSdkClient)
            }
        }
        continuousAmapLocationManager = ContinuousAmapLocationManager(factory, object : AmapApiKeyProvider {
            override suspend fun getAmapApiKey() = if (prefs.getBoolean("realSource", false))
                dependencies.amapApiKeyProvider().getAmapApiKey() else "isolated-probe"
        }, AndroidLocationRuntimeReadiness(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == START_PROBE) {
            applyControls(intent)
            sessionState.value = loggedIn(probeAccount())
            trackingManager.onSessionStateChanged(sessionState.value)
            prefs.edit().putBoolean("orderEnded", false).commit()
            reportingManager.onOrderStarted(activeOrderId)
            reportingManager.startReporting(OrderKey(activeOrderId))
            return START_STICKY
        }
        if (intent?.action == CONTROL_PROBE) {
            applyControls(intent)
            return START_STICKY
        }
        if (intent?.action == STOP_PROBE) {
            trace("stop_requested")
            prefs.edit().remove("session").commit()
            reportingManager.stopReporting()
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun applyControls(intent: Intent) {
        val edit = prefs.edit()
        listOf("realSource", "sdkForeground", "sdkLogs", "rawSdkClient", "stall", "httpFail", "httpSuspend", "locationEnabled", "permissionGranted").forEach { key ->
            if (intent.hasExtra(key)) {
                val value = intent.getBooleanExtra(key, false)
                edit.putBoolean(key, value)
                trace("gate", "name" to key, "value" to value,
                    "simulated" to (key !in setOf("realSource", "sdkForeground", "sdkLogs", "rawSdkClient")),
                    "appliesTo" to if (key in setOf("sdkLogs", "rawSdkClient")) "new_client" else "current_control")
            }
        }
        if (intent.hasExtra("errorCode")) {
            val value = intent.getIntExtra("errorCode", 0)
            edit.putInt("errorCode", value)
            trace("gate", "name" to "errorCode", "value" to value, "simulated" to true)
        }
        if (intent.hasExtra("accuracy")) {
            val value = intent.getFloatExtra("accuracy", 50f)
            edit.putFloat("accuracy", value)
            trace("gate", "name" to "accuracy", "value" to value, "simulated" to true)
        }
        if (intent.hasExtra("httpDelayMs")) {
            val value = intent.getLongExtra("httpDelayMs", 0L).coerceAtLeast(0L)
            edit.putLong("httpDelayMs", value)
            trace("gate", "name" to "httpDelayMs", "value" to value, "simulated" to true)
        }
        edit.commit()
        intent.getStringExtra("auth")?.let { command ->
            trace("gate", "name" to "auth", "value" to command, "simulated" to true)
            sessionState.value = when (command) {
                "unknown" -> SessionState.Unknown
                "ready" -> loggedIn(probeAccount())
                "logout" -> SessionState.LoggedOut
                "switch" -> {
                    val userId = if (probeAccount().userId == identity.userId) identity.userId + 1 else identity.userId
                    prefs.edit().putInt("userId", userId).commit()
                    loggedIn(probeAccount())
                }
                else -> error("unknown probe auth control")
            }
        }
        when (intent.getStringExtra("order")) {
            "end" -> {
                trace("gate", "name" to "order", "value" to "end", "orderId" to activeOrderId)
                prefs.edit().putBoolean("orderEnded", true).commit()
                reportingManager.onOrderEnded(activeOrderId)
            }
            "switch" -> {
                activeOrderId = if (activeOrderId == ORDER_ID) SECOND_ORDER_ID else ORDER_ID
                prefs.edit().putBoolean("orderEnded", false).commit()
                trace("gate", "name" to "order", "value" to "switch", "orderId" to activeOrderId)
                reportingManager.onOrderStarted(activeOrderId)
                reportingManager.startReporting(OrderKey(activeOrderId))
            }
        }
        if (intent.getBooleanExtra("appVisible", false)) trackingManager.onAppVisible()
        if (intent.getBooleanExtra("resumeRecovery", false)) {
            trace("gate", "name" to "resumeRecovery", "value" to true)
            trackingManager.resumePendingRecovery()
        }
        if (intent.getBooleanExtra("actionLocation", false)) probeScope.launch {
            trace("action_start")
            try {
                when (val result = continuousAmapLocationManager.acquireServiceOrderLocation()) {
                    is LocationAcquisition.Success -> trace("action_end", "outcome" to "success", "accuracy" to result.location.accuracy)
                    is LocationAcquisition.Failure -> trace("action_end", "outcome" to "failure", "reason" to result.reason.name)
                }
            } catch (cancelled: CancellationException) {
                trace("action_end", "outcome" to "cancelled")
                throw cancelled
            }
        }
    }

    private fun probeAccount() = identity.copy(userId = prefs.getInt("userId", identity.userId))
    private fun loggedIn(account: LocationAccount) = SessionState.LoggedIn(
        User(companyId = account.companyId, accountId = account.accountId, userId = account.userId),
    )

    override fun onTaskRemoved(rootIntent: Intent?) {
        trace("task_removed")
        prefs.edit().remove("session").commit()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        trace("service_destroy")
        try { super.onDestroy() } finally {
            sdkLogSessions.toList().forEach { it.close("service_destroy") }
            probeScope.cancel()
        }
    }

    private fun trace(event: String, vararg attributes: Pair<String, Any?>) {
        // All trace producers share Main with the SDK-log consumer, including rotation.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            traceHandler.post { trace(event, *attributes) }
            return
        }
        writeTrace(event, *attributes)
    }

    private fun writeTrace(event: String, vararg attributes: Pair<String, Any?>): Boolean {
        val json = JSONObject().put("event", event).put("pid", Process.myPid())
            .put("client", traceClientId ?: JSONObject.NULL)
            .put("wallMillis", System.currentTimeMillis())
            .put("elapsedRealtime", SystemClock.elapsedRealtime())
            .put("uptimeMillis", SystemClock.uptimeMillis())
        attributes.forEach { (key, value) ->
            val finite = when (value) {
                is Float -> value.takeIf { it.isFinite() }
                is Double -> value.takeIf { it.isFinite() }
                else -> value
            }
            json.put(key, finite ?: JSONObject.NULL)
        }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        json.put("screenOn", power.isInteractive).put("deviceIdle", power.isDeviceIdleMode)
            .put("powerSave", power.isPowerSaveMode).put("batteryExempt", power.isIgnoringBatteryOptimizations(packageName))
        val line = json.toString()
        Log.i(LOG_TAG, line)
        // Debug fixture only: retain unplugged observations beyond the system logcat ring.
        // Two bounded files contain event metadata, never coordinates or real care records.
        return runCatching {
            if (traceFile.length() >= MAX_TRACE_BYTES) {
                val previous = File(filesDir, "background-location-probe.previous.jsonl")
                check(!previous.exists() || previous.delete())
                check(traceFile.renameTo(previous))
            }
            traceFile.appendText(line + "\n")
        }.onFailure { Log.w(LOG_TAG, "Probe trace unavailable", it) }.isSuccess
    }

    private inner class SdkLogSession(private val sdk: SdkAmapClientHandle, private val clientId: Int) {
        private val capture = ProbeSdkLogCapture(elapsedRealtime = SystemClock::elapsedRealtime,
            uptimeMillis = SystemClock::uptimeMillis)
        private val callback = ILogCallback { message, wallMillis -> capture.onLog(message, wallMillis) }
        private var closed = false
        // Main rather than Main.immediate prevents trySend from running trace I/O inline in an SDK callback.
        private val consumer = probeScope.launch(Dispatchers.Main) {
            for (event in capture.events) consume(event)
        }

        fun register() {
            sdkLogSessions += this
            trace("sdk_log_register_attempt", "client" to null, "registeredByClient" to clientId,
                "scope" to "process_global")
            val returned = runCatching { sdk.addLogCallback(callback) }.isSuccess
            trace("sdk_log_register_returned", "client" to null, "registeredByClient" to clientId, "returned" to returned,
                "scope" to "process_global", "acknowledged" to false)
            if (!returned) close("registration_failed")
        }

        private fun consume(event: ProbeSdkLogEvent) {
            val written = runCatching {
                writeTrace("sdk_log", "client" to null, "registeredByClient" to clientId, "scope" to "process_global",
                    "kind" to event.kind.traceName,
                    "receivedElapsedRealtime" to event.receivedElapsedRealtime,
                    "receivedUptimeMillis" to event.receivedUptimeMillis,
                    "logWallMillis" to event.logWallMillis)
            }.getOrDefault(false)
            capture.onConsumed(written)
        }

        fun close(reason: String) {
            if (closed) return
            closed = true
            val stoppedAt = SystemClock.elapsedRealtime()
            capture.close()
            val removed = runCatching { sdk.removeLogCallback(callback) }.isSuccess
            // Main serializes this bounded drain with the consumer. Concurrent callback sends
            // after close fail; an in-flight callback makes the final completeness flag false.
            while (true) consume(capture.events.tryReceive().getOrNull() ?: break)
            consumer.cancel()
            val status = capture.snapshot()
            trace("sdk_log_closed", "client" to null, "registeredByClient" to clientId,
                "scope" to "process_global", "reason" to reason,
                "stoppedAtElapsedRealtime" to stoppedAt, "removeReturned" to removed,
                "matched" to status.matched, "enqueued" to status.enqueued, "consumed" to status.consumed,
                "dropped" to status.dropped, "pending" to status.pending, "inFlight" to status.inFlight,
                "afterClose" to status.afterClose, "callbackFailures" to status.callbackFailures,
                "writeFailures" to status.writeFailures, "localCaptureComplete" to (removed && status.complete))
            sdkLogSessions -= this
            // A failed removal keeps the process lease occupied; never stack another registration.
            if (removed) SDK_LOG_CAPTURE_ACTIVE.set(false)
        }
    }

    private inner class TracedClient(private val delegate: AmapClientHandle, private val clientId: Int,
        sdkLogsRequested: Boolean, private val nativeSdkBoundary: Boolean) : AmapClientHandle {
        private val sdkLogs = if (sdkLogsRequested && delegate is SdkAmapClientHandle &&
            SDK_LOG_CAPTURE_ACTIVE.compareAndSet(false, true)) {
            SdkLogSession(delegate, clientId).also { it.register() }
        } else {
            if (sdkLogsRequested) trace("sdk_log_registration_skipped", "client" to clientId,
                "reason" to if (delegate !is SdkAmapClientHandle) "not_raw_sdk_handle" else "process_capture_active")
            null
        }
        private var originalListener: AMapLocationListener? = null
        private var tracedListener: AMapLocationListener? = null
        private var sdkForegroundRequested = false
        override fun sdkVersion() = delegate.sdkVersion()
        override fun notifyAppEnterForeBack(foreground: Boolean) {
            trace("sdk_visibility_attempt", "client" to clientId, "foreground" to foreground)
            delegate.notifyAppEnterForeBack(foreground)
        }
        override fun setLocationOption(option: AMapLocationClientOption) {
            trace("client_option", "client" to clientId, "intervalMs" to option.interval,
                "once" to option.isOnceLocation, "httpTimeoutMs" to option.httpTimeOut,
                "mode" to option.locationMode.name, "cache" to option.isLocationCacheEnable,
                "onceLatest" to option.isOnceLocationLatest)
            delegate.setLocationOption(option)
        }
        override fun setLocationListener(listener: AMapLocationListener) {
            originalListener = listener
            val wrapped = AMapLocationListener { raw ->
                trace("sdk_callback", "client" to clientId, "errorCode" to raw?.errorCode,
                    "nativeSdkBoundary" to nativeSdkBoundary,
                    "accuracy" to raw?.accuracy, "locationTime" to raw?.time, "locationType" to raw?.locationType,
                    // Inspect SDK tick/UTC alongside type; Location.time alone may be refreshed for reused results.
                    "locationTickNanos" to raw?.locationTickTime, "locationUtcTime" to raw?.locationUtcTime,
                    "trustedLevel" to raw?.trustedLevel, "lastLocation" to raw?.isFixLastLocation)
                listener.onLocationChanged(raw)
            }
            tracedListener = wrapped
            delegate.setLocationListener(wrapped)
        }
        override fun unRegisterLocationListener(listener: AMapLocationListener) {
            if (originalListener !== listener) return
            tracedListener?.let(delegate::unRegisterLocationListener)
            originalListener = null
            tracedListener = null
            trace("client_unregister", "client" to clientId)
        }
        override fun startLocation() {
            if (!sdkForegroundRequested && prefs.getBoolean("sdkForeground", false)) {
                val sdk = checkNotNull(delegate as? SdkAmapClientHandle) { "SDK foreground trial requires real AMap" }
                NotificationManagerCompat.from(this@BackgroundLocationProbeService).createNotificationChannel(
                    NotificationChannelCompat.Builder(SDK_FOREGROUND_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(getString(R.string.location_probe_sdk_channel)).setShowBadge(false).build(),
                )
                val notification = NotificationCompat.Builder(this@BackgroundLocationProbeService, SDK_FOREGROUND_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle(getString(R.string.location_probe_sdk_notification))
                    .setContentText(getString(R.string.location_tracking_notification_content))
                    .setOngoing(true).build()
                sdkForegroundRequested = true
                // The SDK swallows some start failures; only system service records prove promotion.
                trace("sdk_fg_enable_requested", "client" to clientId, "notificationId" to SDK_FOREGROUND_NOTIFICATION)
                sdk.enableBackgroundLocation(SDK_FOREGROUND_NOTIFICATION, notification)
            }
            trace("client_start", "client" to clientId)
            delegate.startLocation()
        }
        override fun stopLocation() { trace("client_stop", "client" to clientId); delegate.stopLocation() }
        override fun onDestroy() {
            try {
                if (sdkForegroundRequested) {
                    sdkForegroundRequested = false
                    trace("sdk_fg_disable_requested", "client" to clientId)
                    (delegate as SdkAmapClientHandle).disableBackgroundLocation()
                }
            } finally {
                sdkLogs?.close("client_destroy")
                trace("client_destroy", "client" to clientId)
                if (traceClientId == clientId) traceClientId = null
                delegate.onDestroy()
            }
        }
    }

    private inner class ProbeClient : AmapClientHandle {
        private val handler = Handler(Looper.getMainLooper())
        private var listener: AMapLocationListener? = null
        private var interval = 30_000L
        private var running = false
        private val callback = object : Runnable {
            override fun run() {
                if (!running) return
                if (!prefs.getBoolean("stall", false)) {
                    val sample = AMapLocation("network").apply {
                        latitude = 30.0; longitude = 120.0
                        accuracy = prefs.getFloat("accuracy", 50f)
                        errorCode = prefs.getInt("errorCode", AMapLocation.LOCATION_SUCCESS)
                        time = System.currentTimeMillis()
                        locationType = AMapLocation.LOCATION_TYPE_WIFI
                        coordType = AMapLocation.COORD_TYPE_GCJ02
                        trustedLevel = AMapLocation.TRUSTED_LEVEL_HIGH
                    }
                    listener?.onLocationChanged(sample)
                }
                handler.postDelayed(this, interval)
            }
        }
        override fun setLocationOption(option: AMapLocationClientOption) { interval = option.interval }
        override fun setLocationListener(listener: AMapLocationListener) { this.listener = listener }
        override fun unRegisterLocationListener(listener: AMapLocationListener) { if (this.listener === listener) this.listener = null }
        override fun startLocation() { if (!running) { running = true; handler.post(callback) } }
        override fun stopLocation() { running = false; handler.removeCallbacks(callback) }
        override fun onDestroy() { stopLocation(); listener = null }
    }

    companion object {
        const val PREFERENCES = "background_location_probe"
        const val START_PROBE = "debug.location.PROBE_START"
        const val CONTROL_PROBE = "debug.location.PROBE_CONTROL"
        const val STOP_PROBE = "debug.location.PROBE_STOP"
        private const val ORDER_ID = 9_000_001L
        private const val SECOND_ORDER_ID = 9_000_002L
        private const val LOG_TAG = "LocationProbe"
        private const val MAX_TRACE_BYTES = 1_048_576L
        private const val SDK_FOREGROUND_CHANNEL = "location_probe_sdk_foreground"
        private const val SDK_FOREGROUND_NOTIFICATION = 9_001
        private val SDK_LOG_CAPTURE_ACTIVE = AtomicBoolean(false)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ProbeLocationDependencies {
    fun amapClientFactory(): AmapLocationClientFactory
    fun amapApiKeyProvider(): AmapApiKeyProvider
}
