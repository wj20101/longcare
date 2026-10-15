package com.ytone.longcare.features.location.service

import android.app.PendingIntent
import android.app.Notification
import android.os.PowerManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import com.ytone.longcare.feature.location.R
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRuntimeReadiness
import com.ytone.longcare.domain.location.LocationAcquisition
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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

    @Volatile private var foregroundEntered = false
    private var recoveryJob: Job? = null
    private var lastNotificationText: Int? = null
    private var isKeepAliveStarted = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectorJob: Job? = null
    @Volatile private var currentGeneration: Long = 0L
    private var currentOwner: String? = null
    private var currentOrderId: Long? = null
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
                if (!privacyConsentManager.isPrivacyConsented || !runtimeReadiness.hasLocationPermission() ||
                    !runtimeReadiness.isLocationServiceEnabled()) { stopUnclaimedService(); return@launch }
                // Authorization may change while reading storage. Never restore a captured old account.
                val currentUser = userSessionRepository.sessionState.value.user
                if (currentUser == null || LocationAccount(currentUser.companyId, currentUser.accountId, currentUser.userId) != identity) {
                    stopUnclaimedService(); return@launch
                }
                withContext(Dispatchers.Main.immediate) {
                    val restored = reportingManager.restore(record, requireNotNull(identity))
                    if (restored == null) { stopUnclaimedService(); return@withContext }
                    val (session, generation) = restored
                    startKeepAlive(session.owner, session.orderId, generation)
                }
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
                val sessionChanged = serviceGeneration != currentGeneration ||
                    owner != currentOwner || orderId != currentOrderId
                currentGeneration = serviceGeneration
                currentOwner = owner
                currentOrderId = orderId
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
        currentOrderId = orderId
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
            keepAliveManager.onServiceStopped(serviceGeneration)
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
                check(currentAccount() == session.account) { "location_account_changed" }
                val locations = continuousAmapLocationManager.startContinuousLocation(owner = owner)
                val evaluator = LocationSampleEvaluator(
                    orderId = orderId,
                    sessionStartedAt = locationClock.currentTimeMillis(),
                    clock = locationClock,
                )
                reportingManager.updateStatus(owner, LocationReportingStatus.LOCATING)
                updateNotification(R.string.location_tracking_locating)
                coroutineScope {
                    val healthJob = launch { watchSampling(owner, session) }
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
                                ).also { result ->
                                    currentCoroutineContext().ensureActive()
                                    if (!session.canUpload() || currentAccount() != session.account) return@also
                                    val healthy = result is com.ytone.longcare.model.result.ApiResult.Success
                                    reportingManager.updateStatus(owner, if (healthy) LocationReportingStatus.ACTIVE else LocationReportingStatus.NETWORK_ERROR)
                                    updateNotification(if (healthy) R.string.location_tracking_notification_content else R.string.location_tracking_network_wait)
                                }
                            },
                            evaluator = evaluator,
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
                keepAliveManager.onServiceStopped(serviceGeneration)
                updateNotification(R.string.location_tracking_interrupted)
            }
        }
        collectorJob = job
        session.attach(job)
        job.start()
    }

    private fun currentAccount(): LocationAccount? = userSessionRepository.sessionState.value.user?.let {
        LocationAccount(it.companyId, it.accountId, it.userId)
    }

    private suspend fun watchSampling(owner: String, session: com.ytone.longcare.features.location.reporting.LocationReportingSession) {
        var recoveredAt: Long? = null
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            delay(30_000L)
            if (!session.canUpload()) continue
            val power = getSystemService(POWER_SERVICE) as PowerManager
            check(privacyConsentManager.isPrivacyConsented && runtimeReadiness.hasLocationPermission() &&
                runtimeReadiness.isLocationServiceEnabled() && currentAccount() == session.account) { "location_runtime_not_ready" }
            if (power.isDeviceIdleMode) continue
            // An error callback also ends SDK silence; the next independent stall gets one rebuild.
            if (continuousAmapLocationManager.lastCallbackTime() > (recoveredAt ?: Long.MAX_VALUE)) recoveredAt = null
            val event = continuousAmapLocationManager.latestEvent()
            if (event is LocationAcquisition.Success) {
                val sample = event.location
                if (ServiceOrderLocationQuality.rejection(sample, locationClock.currentTimeMillis(), locationClock.elapsedRealtime(),
                        maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS) != null) {
                    reportingManager.updateStatus(owner, LocationReportingStatus.LOW_PRECISION)
                    updateNotification(R.string.location_tracking_quality_wait)
                }
            } else if (event is LocationAcquisition.Failure) {
                when (event.reason) {
                    com.ytone.longcare.domain.location.LocationFailure.PERMISSION,
                    com.ytone.longcare.domain.location.LocationFailure.CONFIGURATION -> error("amap_runtime_unavailable")
                    com.ytone.longcare.domain.location.LocationFailure.NETWORK,
                    com.ytone.longcare.domain.location.LocationFailure.UNAVAILABLE -> {
                        reportingManager.updateStatus(owner, LocationReportingStatus.LOCATING)
                        updateNotification(R.string.location_tracking_locating)
                    }
                    else -> {
                        reportingManager.updateStatus(owner, LocationReportingStatus.LOW_PRECISION)
                        updateNotification(R.string.location_tracking_quality_wait)
                    }
                }
            }
            if (recoveredAt == null && continuousAmapLocationManager.recoverStalledSampling(owner)) {
                recoveredAt = locationClock.elapsedRealtime()
                reportingManager.updateStatus(owner, LocationReportingStatus.LOCATING)
                updateNotification(R.string.location_tracking_locating)
                LocationEventTracker.trackEvent(LocationEventTracker.EventType.SDK_RECOVERED,
                    mapOf(LocationEventTracker.Attribute.ORDER_ID to session.orderId))
            } else if (recoveredAt != null && locationClock.elapsedRealtime() -
                continuousAmapLocationManager.lastCallbackTime() >= 90_000L) {
                // One rebuild per stall. Continued silence requires a visible retry, not a restart loop.
                error("amap_no_callback_after_recovery")
            }
        }
    }

    private fun updateNotification(text: Int) {
        if (lastNotificationText == text || !foregroundEntered) return
        lastNotificationText = text
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, createNotification(text))
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
            currentOrderId = null
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
        keepAliveManager.onServiceStopped(currentGeneration)
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
