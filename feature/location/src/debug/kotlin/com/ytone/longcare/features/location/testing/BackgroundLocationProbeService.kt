package com.ytone.longcare.features.location.testing

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.domain.location.*
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.location.core.LocationForegroundServiceController
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.manager.AmapClientHandle
import com.ytone.longcare.features.location.manager.AmapLocationClientFactory
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.features.location.service.LocationTrackingService
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug-only native lifecycle fixture. Executes the production Service and reporting logic,
 * while order state and uploads stay local. Never touches the real account or care backend.
 * Use realSource=true for real AMap samples; deterministic samples only verify lifecycle.
 */
class BackgroundLocationProbeService : LocationTrackingService() {
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val identity = LocationAccount(901, 902, 903)

    override fun onCreate() {
        super.onCreate()
        prefs.edit().putInt("boots", prefs.getInt("boots", 0) + 1).commit()
        val recordStore = object : LocationRecoveryStore {
            override suspend fun read(): LocationRecoveryRecord? = prefs.getString("session", null)?.let {
                LocationRecoveryRecord(identity, ORDER_ID, it)
            }
            override suspend fun save(record: LocationRecoveryRecord) {
                prefs.edit().putString("session", record.sessionId).commit()
            }
            override suspend fun clear(sessionId: String?) {
                if (sessionId == null || prefs.getString("session", null) == sessionId) {
                    prefs.edit().remove("session").commit()
                }
            }
        }
        recoveryStore = recordStore
        userSessionRepository = object : UserSessionRepository {
            override val sessionState = MutableStateFlow<SessionState>(SessionState.LoggedIn(
                User(companyId = identity.companyId, accountId = identity.accountId, userId = identity.userId),
            ))
            override suspend fun login(user: User) = error("probe does not change account")
            override suspend fun updateUser(user: User) = error("probe does not change account")
            override suspend fun logout() = error("probe does not change account")
        }
        val orders = Proxy.newProxyInstance(OrderRepository::class.java.classLoader,
            arrayOf(OrderRepository::class.java)) { _, method, _ ->
            check(method.name == "getOrderState") { "probe does not call business APIs" }
            ApiResult.Success(ServiceOrderStateModel(ORDER_ID, ServiceOrderStateModel.STATE_IN_PROGRESS))
        } as OrderRepository
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
        locationRepository = object : LocationRepository {
            override suspend fun addPosition(orderId: Long, latitude: Double, longitude: Double): ApiResult<Unit> {
                check(orderId == ORDER_ID)
                // Store only timing/counters, never coordinates, tokens or care order records.
                prefs.edit().putInt("uploads", prefs.getInt("uploads", 0) + 1)
                    .putLong("lastUpload", System.currentTimeMillis()).commit()
                return ApiResult.Success(Unit)
            }
        }
        if (!prefs.getBoolean("realSource", false)) {
            val factory = object : AmapLocationClientFactory(this, privacyConsentManager) {
                override fun create(apiKey: String): AmapClientHandle {
                    prefs.edit().putInt("clients", prefs.getInt("clients", 0) + 1).commit()
                    return ProbeClient()
                }
            }
            continuousAmapLocationManager = ContinuousAmapLocationManager(factory, object : AmapApiKeyProvider {
                override suspend fun getAmapApiKey() = "isolated-probe"
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == START_PROBE) {
            reportingManager.setAccount(identity)
            reportingManager.onOrderStarted(ORDER_ID)
            reportingManager.startReporting(OrderKey(ORDER_ID))
            return START_STICKY
        }
        if (intent?.action == STOP_PROBE) {
            prefs.edit().remove("session").commit()
            reportingManager.stopReporting()
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        prefs.edit().remove("session").commit()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        probeScope.cancel()
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
                        latitude = 30.0; longitude = 120.0; accuracy = 50f
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
        const val STOP_PROBE = "debug.location.PROBE_STOP"
        private const val ORDER_ID = 9_000_001L
    }
}
