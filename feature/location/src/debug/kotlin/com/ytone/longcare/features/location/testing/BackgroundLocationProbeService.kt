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
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
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
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.features.location.service.LocationTrackingService
import com.ytone.longcare.features.location.session.LocationSessionLifecycleObserver
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import java.lang.reflect.Proxy
import java.io.IOException
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
        val factory = object : AmapLocationClientFactory(this, privacyConsentManager) {
            override fun create(apiKey: String): AmapClientHandle {
                val clientId = prefs.getInt("clients", 0) + 1
                prefs.edit().putInt("clients", clientId).commit()
                val realSource = prefs.getBoolean("realSource", false)
                val client = if (realSource) dependencies.amapClientFactory().create(apiKey) else ProbeClient()
                traceClientId = clientId
                trace("client_create", "client" to clientId, "source" to if (realSource) "amap" else "fake")
                return TracedClient(client, clientId)
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
        listOf("realSource", "stall", "httpFail", "httpSuspend", "locationEnabled", "permissionGranted").forEach { key ->
            if (intent.hasExtra(key)) {
                val value = intent.getBooleanExtra(key, false)
                edit.putBoolean(key, value)
                trace("gate", "name" to key, "value" to value, "simulated" to (key != "realSource"))
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
        super.onDestroy()
        probeScope.cancel()
    }

    private fun trace(event: String, vararg attributes: Pair<String, Any?>) {
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
        Log.i(LOG_TAG, json.toString())
    }

    private inner class TracedClient(private val delegate: AmapClientHandle, private val clientId: Int) : AmapClientHandle {
        private var originalListener: AMapLocationListener? = null
        private var tracedListener: AMapLocationListener? = null
        override fun sdkVersion() = delegate.sdkVersion()
        override fun setLocationOption(option: AMapLocationClientOption) {
            trace("client_option", "client" to clientId, "intervalMs" to option.interval,
                "once" to option.isOnceLocation, "httpTimeoutMs" to option.httpTimeOut)
            delegate.setLocationOption(option)
        }
        override fun setLocationListener(listener: AMapLocationListener) {
            originalListener = listener
            val wrapped = AMapLocationListener { raw ->
                trace("sdk_callback", "client" to clientId, "errorCode" to raw?.errorCode,
                    "accuracy" to raw?.accuracy, "locationTime" to raw?.time, "locationType" to raw?.locationType)
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
        override fun startLocation() { trace("client_start", "client" to clientId); delegate.startLocation() }
        override fun stopLocation() { trace("client_stop", "client" to clientId); delegate.stopLocation() }
        override fun onDestroy() {
            trace("client_destroy", "client" to clientId)
            if (traceClientId == clientId) traceClientId = null
            delegate.onDestroy()
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
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ProbeLocationDependencies {
    fun amapClientFactory(): AmapLocationClientFactory
    fun amapApiKeyProvider(): AmapApiKeyProvider
}
