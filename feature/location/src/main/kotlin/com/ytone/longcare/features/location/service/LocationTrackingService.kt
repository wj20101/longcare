package com.ytone.longcare.features.location.service

import android.Manifest
import android.app.PendingIntent
import android.app.Notification
import android.os.PowerManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ytone.longcare.feature.location.R
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRuntimeReadiness
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.features.location.reporting.LocationReportingStatus
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.reporting.LocationClock
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.features.location.reporting.LocationSampleEvaluator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@AndroidEntryPoint
open class LocationTrackingService : Service() {

    @Inject
    lateinit var continuousAmapLocationManager: ContinuousAmapLocationManager

    @Inject
    lateinit var keepAliveManager: LocationKeepAliveManager

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var reportingManager: LocationReportingManager

    @Inject
    lateinit var locationClock: LocationClock

    @Inject lateinit var recoveryStore: LocationRecoveryStore
    @Inject lateinit var userSessionRepository: UserSessionRepository
    @Inject lateinit var runtimeReadiness: LocationRuntimeReadiness
    @Inject lateinit var privacyConsentManager: PrivacyConsentManager

    private var foregroundEntered = false
    private var recoveryJob: Job? = null
    private var lastNotificationText: Int? = null
    private var isKeepAliveStarted = false
    // Android commands, SDK state and notifications share the Service's main-thread lifecycle.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null
    private var currentGeneration: Long = 0L
    private var currentOwner: String? = null
    private var cleanupCompleted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logI("📥 收到Intent: action=${intent?.action}")

        when (intent?.action) {
            ACTION_ACQUIRE_KEEP_ALIVE -> {
                val owner = intent.getStringExtra(EXTRA_OWNER) ?: UNKNOWN_OWNER
                val serviceGeneration = intent.getLongExtra(EXTRA_GENERATION, 0L)
                if (keepAliveManager.hasNewerRequestThan(owner, serviceGeneration)) return START_STICKY
                val orderId = intent.takeIf { it.hasExtra(EXTRA_ORDER_ID) }
                    ?.getLongExtra(EXTRA_ORDER_ID, INVALID_ORDER_ID)
                    ?.takeIf { it > 0L }
                if (orderId == null || reportingManager.sessionFor(owner, orderId) == null) restoreFromStore()
                else {
                    // A new visible request supersedes any pending cold-start storage read.
                    recoveryJob?.cancel()
                    recoveryJob = null
                    startKeepAlive(owner, orderId, serviceGeneration)
                }
            }

            else -> restoreFromStore()
        }
        return START_STICKY
    }

    /** Establish the notification before storage/network work; sticky restart Intents may be null. */
    private fun restoreFromStore() {
        if (recoveryJob?.isActive == true || isKeepAliveStarted) return
        try { enterForeground() }
        catch (error: Exception) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.SERVICE_START_ERROR, error)
            stopSelf()
            return
        }
        recoveryJob = serviceScope.launch {
            try {
                val record = recoveryStore.read()
                currentCoroutineContext().ensureActive()
                if (record == null) { stopUnclaimedService(); return@launch }
                val user = userSessionRepository.sessionState.first { it !is SessionState.Unknown }.user
                currentCoroutineContext().ensureActive()
                val identity = user?.let { LocationAccount(it.companyId, it.accountId, it.userId) }
                if (identity != record.account) {
                    recoveryStore.clear(record.sessionId)
                    stopUnclaimedService()
                    return@launch
                }
                if (!privacyConsentManager.isPrivacyConsented) {
                    recoveryStore.clear(record.sessionId)
                    stopUnclaimedService(); return@launch
                }
                if (!runtimeReadiness.hasLocationPermission()) { stopUnclaimedService(); return@launch }
                // Authorization may change while reading storage. Never restore a captured old account.
                val currentUser = userSessionRepository.sessionState.value.user
                if (currentUser == null || LocationAccount(currentUser.companyId, currentUser.accountId, currentUser.userId) != identity) {
                    stopUnclaimedService(); return@launch
                }
                val restored = reportingManager.restore(record, requireNotNull(identity))
                if (restored == null) { stopUnclaimedService(); return@launch }
                val (session, generation) = restored
                startKeepAlive(session.owner, session.orderId, generation)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                LocationEventTracker.trackError(LocationEventTracker.EventType.SERVICE_START_ERROR, error)
                stopUnclaimedService()
            }
        }
    }

    private fun stopUnclaimedService() {
        if (!keepAliveManager.hasAcceptedStart()) stopSelf()
    }

    private fun enterForeground() {
        if (foregroundEntered) return
        createNotificationChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        foregroundEntered = true
        lastNotificationText = R.string.location_tracking_preparing
    }

    private fun startKeepAlive(owner: String, orderId: Long, serviceGeneration: Long) {
        if (isKeepAliveStarted) {
            if (serviceGeneration < currentGeneration) {
                logI("忽略过期定位保活命令 (generation=$serviceGeneration)")
                return
            }
            if (keepAliveManager.onServiceStarted(serviceGeneration)) {
                val sessionChanged = serviceGeneration != currentGeneration || owner != currentOwner
                currentGeneration = serviceGeneration
                currentOwner = owner
                if (sessionChanged || collectorJob?.isActive != true) {
                    updateNotification(R.string.location_tracking_preparing)
                    startCollector(owner, orderId, serviceGeneration)
                }
                logI("定位保活服务已运行，确认新会话 (owner=$owner, generation=$serviceGeneration)")
            } else {
                currentGeneration = serviceGeneration
                stopKeepAlive(stopService = true)
            }
            return
        }

        cleanupCompleted = false
        currentGeneration = serviceGeneration
        currentOwner = owner
        try {
            logI("启动定位前台保活 (owner=$owner)")
            enterForeground()
            check(privacyConsentManager.isPrivacyConsented) { "location_privacy_not_consented" }
            if (!keepAliveManager.onServiceStarted(serviceGeneration)) {
                stopKeepAlive(stopService = true)
                return
            }
            isKeepAliveStarted = true
            startCollector(owner, orderId, serviceGeneration)
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.SERVICE_START_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            // Dispatch can succeed before Android rejects the actual foreground start.
            // Publish failure before cleanup clears the owner, and allow a visible retry.
            reportingManager.onServiceInterrupted(owner)
            stopKeepAlive(stopService = true)
        }
    }

    private fun startCollector(owner: String, orderId: Long, serviceGeneration: Long) {
        val session = reportingManager.sessionFor(owner, orderId)
        if (session == null) {
            keepAliveManager.releaseFromService(owner, serviceGeneration)
            return
        }
        val previousJob = collectorJob
        previousJob?.cancel()
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            previousJob?.cancelAndJoin()
            try {
                session.awaitReady()
                if (currentGeneration != serviceGeneration) return@launch
                // The session owns its authorized account; a delayed login observer cannot rebind it.
                if (userSessionRepository.sessionState.value !is SessionState.Unknown)
                    check(currentAccount() == session.account) { "location_account_changed" }
                val samplingEnabled = MutableStateFlow(LocationSamplingState(false))
                val initiallyEnabled = updateSampling(owner, session, samplingEnabled)
                val locations = continuousAmapLocationManager.startContinuousLocation(owner, initiallyEnabled)
                    .onEach { event ->
                        if (updateSampling(owner, session, samplingEnabled)) updateAcquisitionStatus(owner, event)
                    }
                    .filterIsInstance<LocationAcquisition.Success>().map { it.location }
                val evaluator = LocationSampleEvaluator(
                    orderId = orderId,
                    sessionStartedAt = locationClock.currentTimeMillis(),
                    clock = locationClock,
                )
                coroutineScope {
                    val healthJob = launch { watchSampling(owner, session, samplingEnabled) }
                    try {
                        ServiceLocationSession(
                            session = session,
                            locations = locations,
                            uploadLocation = { location ->
                                currentCoroutineContext().ensureActive()
                                if (!runtimeReadiness.hasLocationPermission() || !runtimeReadiness.isLocationServiceEnabled() ||
                                    !privacyConsentManager.isPrivacyConsented || currentAccount() != session.account) {
                                    throw CancellationException("Location authorization changed")
                                }
                                locationRepository.addPosition(
                                    orderId = orderId,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                )
                            },
                            onUploadResult = { result ->
                                if (session.canUpload() && currentAccount() == session.account) {
                                    val healthy = result is com.ytone.longcare.model.result.ApiResult.Success
                                    reportingManager.updateStatus(owner, if (healthy) LocationReportingStatus.ACTIVE else LocationReportingStatus.NETWORK_ERROR)
                                    updateNotification(if (healthy) R.string.location_tracking_notification_content else R.string.location_tracking_network_wait)
                                }
                            },
                            evaluator = evaluator,
                            elapsedRealtime = locationClock::elapsedRealtime,
                            samplingEnabled = samplingEnabled,
                        ).run()
                    } finally { healthJob.cancel() }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.REPORTING_TASK_ERROR,
                    throwable = error,
                    extras = mapOf(
                        LocationEventTracker.Attribute.ORDER_ID to orderId,
                        LocationEventTracker.Attribute.ERROR_TYPE to error.javaClass.simpleName,
                    ),
                )
                reportingManager.onServiceInterrupted(owner)
                if (currentOwner == owner && currentGeneration == serviceGeneration) stopKeepAlive(stopService = true)
            }
        }
        collectorJob = job
        session.attach(job)
        job.start()
    }

    private fun currentAccount(): LocationAccount? = userSessionRepository.sessionState.value.user?.let {
        LocationAccount(it.companyId, it.accountId, it.userId)
    }

    /** Temporary gates pause the SDK; terminal authorization loss stops the actual foreground service. */
    private suspend fun updateSampling(
        owner: String,
        session: com.ytone.longcare.features.location.reporting.LocationReportingSession,
        enabled: MutableStateFlow<LocationSamplingState>,
    ): Boolean {
        currentCoroutineContext().ensureActive()
        if (!privacyConsentManager.isPrivacyConsented) {
            reportingManager.stopReporting(owner)
            stopKeepAlive(stopService = true)
            throw CancellationException("Location privacy revoked")
        }
        if (!runtimeReadiness.hasLocationPermission()) {
            reportingManager.onServiceInterrupted(owner)
            stopKeepAlive(stopService = true)
            throw CancellationException("Location permission revoked")
        }
        val unknown = userSessionRepository.sessionState.value is SessionState.Unknown
        check(unknown || currentAccount() == session.account) { "location_account_changed" }
        val locationEnabled = runtimeReadiness.isLocationServiceEnabled()
        val ready = !unknown && session.canUpload() && locationEnabled
        if (enabled.value.enabled != ready) {
            enabled.value = LocationSamplingState(ready,
                if (ready) locationClock.elapsedRealtime() else enabled.value.resumedAtElapsedRealtime)
            continuousAmapLocationManager.setSamplingEnabled(owner, ready)
        }
        if (!ready) {
            reportingManager.updateStatus(owner, LocationReportingStatus.PAUSED)
            updateNotification(if (locationEnabled) R.string.location_tracking_authorization_wait else R.string.location_tracking_location_off)
        } else if (reportingManager.status.value == LocationReportingStatus.PAUSED ||
            reportingManager.status.value == LocationReportingStatus.PREPARING) {
            reportingManager.updateStatus(owner, LocationReportingStatus.LOCATING)
            updateNotification(R.string.location_tracking_locating)
        }
        return ready
    }

    private fun updateAcquisitionStatus(owner: String, event: LocationAcquisition) {
        val (status, notification) = when (event) {
            is LocationAcquisition.Success -> {
                if (ServiceOrderLocationQuality.rejection(event.location, locationClock.currentTimeMillis(),
                        locationClock.elapsedRealtime(), maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS) == null) return
                LocationReportingStatus.LOW_PRECISION to R.string.location_tracking_quality_wait
            }
            is LocationAcquisition.Failure -> when (event.reason) {
                LocationFailure.PERMISSION, LocationFailure.CONFIGURATION -> error("amap_runtime_unavailable")
                LocationFailure.NETWORK, LocationFailure.UNAVAILABLE -> LocationReportingStatus.LOCATING to R.string.location_tracking_locating
                else -> LocationReportingStatus.LOW_PRECISION to R.string.location_tracking_quality_wait
            }
        }
        reportingManager.updateStatus(owner, status)
        updateNotification(notification)
    }

    private suspend fun watchSampling(owner: String, session: com.ytone.longcare.features.location.reporting.LocationReportingSession,
        samplingEnabled: MutableStateFlow<LocationSamplingState>) {
        var recoveredAfterCallback: Long? = null
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            delay(30_000L)
            val wasEnabled = samplingEnabled.value.enabled
            if (!updateSampling(owner, session, samplingEnabled)) { recoveredAfterCallback = null; continue }
            if (!wasEnabled) recoveredAfterCallback = null
            val power = getSystemService(POWER_SERVICE) as PowerManager
            if (power.isDeviceIdleMode) continue
            // An error callback also ends SDK silence; the next independent stall gets one rebuild.
            val callbacks = continuousAmapLocationManager.callbackSequence()
            if (recoveredAfterCallback != null && callbacks != recoveredAfterCallback) recoveredAfterCallback = null
            if (recoveredAfterCallback == null && continuousAmapLocationManager.recoverStalledSampling(owner)) {
                // Capture before rebuilding so a synchronous first callback also restores the budget.
                recoveredAfterCallback = callbacks
                reportingManager.updateStatus(owner, LocationReportingStatus.LOCATING)
                updateNotification(R.string.location_tracking_locating)
                LocationEventTracker.trackEvent(LocationEventTracker.EventType.SDK_RECOVERED,
                    mapOf(LocationEventTracker.Attribute.ORDER_ID to session.orderId))
            } else if (recoveredAfterCallback != null && locationClock.elapsedRealtime() -
                continuousAmapLocationManager.lastCallbackTime() >= 90_000L) {
                // Keep the rebuilt listener alive: a delayed SDK callback can recover without a page visit.
                reportingManager.updateStatus(owner, LocationReportingStatus.LOCATING)
                updateNotification(R.string.location_tracking_callback_wait)
            }
        }
    }

    private fun updateNotification(text: Int) {
        if (lastNotificationText == text || !foregroundEntered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, createNotification(text))
            lastNotificationText = text
        } catch (_: SecurityException) {
            // 权限可能在检查后被撤销；通知不可用不应中断采集或上报。
            logI("定位通知更新被系统拒绝，继续当前定位会话")
        }
    }

    private fun stopKeepAlive(stopService: Boolean = false) {
        if (!cleanupCompleted) {
            collectorJob?.cancel()
            collectorJob = null
            recoveryJob?.cancel()
            recoveryJob = null
            cleanupStage(CleanupStage.REMOVE_FOREGROUND) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            isKeepAliveStarted = false
            foregroundEntered = false
            currentOwner = null
            keepAliveManager.onServiceStopped(currentGeneration)
            cleanupCompleted = true
            logI("定位前台保活已停止")
        }
        if (stopService) {
            cleanupStage(CleanupStage.STOP_SERVICE) { stopSelf() }
        }
    }

    private inline fun cleanupStage(stage: CleanupStage, cleanup: () -> Unit) {
        try {
            cleanup()
        } catch (error: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.SERVICE_STOP_ERROR,
                throwable = error,
                extras = mapOf(
                    LocationEventTracker.Attribute.ERROR_TYPE to error.javaClass.simpleName,
                    LocationEventTracker.Attribute.CLEANUP_STAGE to stage.telemetryValue,
                ),
            )
        }
    }

    private fun createNotification(text: Int = R.string.location_tracking_preparing): Notification {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.location_tracking_notification_title))
            .setContentText(getString(text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            builder.setContentIntent(PendingIntentCompat.getActivity(this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT, false))
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(getString(R.string.location_tracking_notification_channel_name))
            .setDescription(
                getString(R.string.location_tracking_notification_channel_description),
            )
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Invalidate synchronously. The existing application-scope writer clears durable identity.
        reportingManager.stopReporting()
        stopKeepAlive(stopService = true)
    }

    override fun onDestroy() {
        currentOwner?.let(reportingManager::onServiceInterrupted)
        stopKeepAlive()
        serviceScope.cancel()
        super.onDestroy()
        logI("✅ LocationTrackingService 已销毁")
    }

    private enum class CleanupStage(val telemetryValue: String) {
        REMOVE_FOREGROUND("remove_foreground"),
        STOP_SERVICE("stop_service"),
    }

    companion object {
        const val ACTION_ACQUIRE_KEEP_ALIVE = "ACTION_ACQUIRE_LOCATION_KEEPALIVE"
        const val EXTRA_OWNER = "EXTRA_OWNER"
        const val EXTRA_GENERATION = "EXTRA_GENERATION"
        const val EXTRA_ORDER_ID = "EXTRA_ORDER_ID"
        private const val UNKNOWN_OWNER = "unknown"
        private const val INVALID_ORDER_ID = -1L
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    }
}
