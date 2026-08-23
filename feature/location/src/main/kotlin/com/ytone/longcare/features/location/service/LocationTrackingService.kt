package com.ytone.longcare.features.location.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ytone.longcare.feature.location.R
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationSampleStore
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var continuousAmapLocationManager: ContinuousAmapLocationManager

    @Inject
    lateinit var locationSampleStore: LocationSampleStore

    @Inject
    lateinit var keepAliveManager: LocationKeepAliveManager

    private var isKeepAliveStarted = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectorJob: Job? = null
    private var currentGeneration: Long = 0L
    private var cleanupCompleted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logI("📥 收到Intent: action=${intent?.action}")

        when (intent?.action) {
            ACTION_ACQUIRE_KEEP_ALIVE -> {
                val owner = intent.getStringExtra(EXTRA_OWNER) ?: UNKNOWN_OWNER
                val serviceGeneration = intent.getLongExtra(EXTRA_GENERATION, 0L)
                startKeepAlive(owner, serviceGeneration)
            }

            else -> {
                logI("📥 收到未知命令: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun startKeepAlive(owner: String, serviceGeneration: Long) {
        if (isKeepAliveStarted) {
            if (serviceGeneration < currentGeneration) {
                logI("忽略过期定位保活命令 (generation=$serviceGeneration)")
                return
            }
            if (keepAliveManager.onServiceStarted(serviceGeneration)) {
                currentGeneration = serviceGeneration
                logI("定位保活服务已运行，确认新会话 (owner=$owner, generation=$serviceGeneration)")
            } else {
                currentGeneration = serviceGeneration
                stopKeepAlive(stopService = true)
            }
            return
        }

        cleanupCompleted = false
        try {
            logI("启动定位前台保活 (owner=$owner)")
            createNotificationChannel()
            val notification = createNotification()
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
            currentGeneration = serviceGeneration
            if (!keepAliveManager.onServiceStarted(serviceGeneration)) {
                stopKeepAlive(stopService = true)
                return
            }
            continuousAmapLocationManager.enableBackgroundLocation(NOTIFICATION_ID, notification)
            isKeepAliveStarted = true
            collectorJob = serviceScope.launch {
                try {
                    continuousAmapLocationManager.startContinuousLocation().collect { location ->
                        locationSampleStore.publish(location)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    LocationEventTracker.trackError(
                        LocationEventTracker.EventType.CACHE_COLLECT_ERROR,
                        throwable = error,
                        extras = mapOf(
                            LocationEventTracker.Attribute.ERROR_TYPE to
                                error.javaClass.simpleName,
                        ),
                    )
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.SERVICE_START_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            stopKeepAlive(stopService = true)
        }
    }

    private fun stopKeepAlive(stopService: Boolean = false) {
        if (!cleanupCompleted) {
            collectorJob?.cancel()
            collectorJob = null
            cleanupStage(CleanupStage.STOP_COLLECTION) {
                continuousAmapLocationManager.stopContinuousLocation()
            }
            cleanupStage(CleanupStage.DISABLE_BACKGROUND) {
                continuousAmapLocationManager.disableBackgroundLocation(true)
            }
            cleanupStage(CleanupStage.DESTROY_CLIENT) {
                continuousAmapLocationManager.destroy()
            }
            cleanupStage(CleanupStage.REMOVE_FOREGROUND) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            isKeepAliveStarted = false
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

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.location_tracking_notification_title))
            .setContentText(getString(R.string.location_tracking_notification_content))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
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

    override fun onDestroy() {
        stopKeepAlive()
        keepAliveManager.onServiceStopped(currentGeneration)
        serviceScope.cancel()
        super.onDestroy()
        isKeepAliveStarted = false
        logI("✅ LocationTrackingService 已销毁")
    }

    private enum class CleanupStage(val telemetryValue: String) {
        STOP_COLLECTION("stop_collection"),
        DISABLE_BACKGROUND("disable_background"),
        DESTROY_CLIENT("destroy_client"),
        REMOVE_FOREGROUND("remove_foreground"),
        STOP_SERVICE("stop_service"),
    }

    companion object {
        const val ACTION_ACQUIRE_KEEP_ALIVE = "ACTION_ACQUIRE_LOCATION_KEEPALIVE"
        const val EXTRA_OWNER = "EXTRA_OWNER"
        const val EXTRA_GENERATION = "EXTRA_GENERATION"
        private const val UNKNOWN_OWNER = "unknown"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    }
}
