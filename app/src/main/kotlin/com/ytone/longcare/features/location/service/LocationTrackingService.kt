package com.ytone.longcare.features.location.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationManager: LocationManager

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var trackingManager: LocationTrackingManager

    // 创建一个与 Service 生命周期绑定的协程作用域
    // 使用 SupervisorJob 确保一个子任务的失败不会影响其他任务
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    private val mainThreadExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 服务被创建时，立即向 Manager 同步状态，确保UI能感知到服务正在运行
        trackingManager.updateTrackingState(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        // 使用 START_NOT_STICKY，避免服务在被意外杀死后自动重启，将控制权交由用户或业务逻辑
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // 权限检查已在UI层完成
    private fun startTracking() {
        if (trackingJob?.isActive == true) {
            logI("定位上报任务已在运行中，无需重复启动。")
            return
        }

        logI("启动定位上报循环...")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("服务已启动，正在准备定位..."))

        trackingJob = serviceScope.launch {
            while (isActive) {
                logI("开始新一轮的定位获取...")
                fetchCurrentLocation()
                // 等待30秒，实现精准的间隔
                delay(30_000L)
            }
        }
    }

    /**
     * 健壮的定位获取方法，带有从 GPS 到网络的回退机制。
     */
    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        val cancellationSignal = CancellationSignal()

        // 1. 优先尝试使用 GPS
        if (LocationManagerCompat.hasProvider(locationManager, LocationManager.GPS_PROVIDER)) {
            logI("正在尝试使用 GPS 获取位置...")
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                LocationManager.GPS_PROVIDER,
                cancellationSignal,
                mainThreadExecutor
            ) { location ->
                if (location != null) {
                    // GPS 成功获取到位置，上报并结束本次流程
                    logI("GPS 获取位置成功。")
                    handleLocationUpdate(location)
                } else {
                    // GPS 失败（例如超时或在室内），自动回退到网络定位
                    logI("GPS 获取位置失败，回退到网络定位...")
                    fetchNetworkLocation(cancellationSignal)
                }
            }
        } else {
            // 如果 GPS 未开启，直接尝试网络定位
            logI("GPS 未开启，直接尝试网络定位...")
            fetchNetworkLocation(cancellationSignal)
        }
    }

    /**
     * 作为回退方案，专门用于获取网络位置的辅助方法。
     */
    @SuppressLint("MissingPermission")
    private fun fetchNetworkLocation(cancellationSignal: CancellationSignal) {
        if (LocationManagerCompat.hasProvider(locationManager, LocationManager.NETWORK_PROVIDER)) {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                LocationManager.NETWORK_PROVIDER,
                cancellationSignal,
                mainThreadExecutor
            ) { location ->
                if (location != null) {
                    logI("网络定位获取位置成功。")
                    handleLocationUpdate(location)
                } else {
                    logE("网络定位也获取位置失败。")
                    updateNotification("获取位置失败，请检查网络和GPS信号。")
                }
            }
        } else {
            logE("GPS 和网络定位均未开启。")
            updateNotification("错误：定位服务均未开启。")
        }
    }

    /**
     * 处理位置更新和上报的统一方法。
     */
    private fun handleLocationUpdate(location: android.location.Location) {
        logI("成功获取到位置: Provider=${location.provider}, Lat=${location.latitude}, Lng=${location.longitude}")
        updateNotification(
            "位置已更新: ${
                String.format(
                    Locale.getDefault(),
                    "%.4f",
                    location.latitude
                )
            }, ${String.format(Locale.getDefault(), "%.4f", location.longitude)}"
        )

        // 在IO线程中执行网络请求
        serviceScope.launch {
            // 这里的 orderId 应该从启动服务的 Intent 中获取或从其他地方获取
            // 为了演示，我们使用一个固定的值
            val orderId = 12345L
            locationRepository.addPosition(orderId, location.latitude, location.longitude)
            logI("位置上报API调用完成。")
        }
    }

    private fun stopTracking() {
        logI("停止定位上报循环...")
        trackingJob?.cancel()
        trackingJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("后台定位服务")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.app_logo_round) // 请务必替换为您的应用图标
            .setOngoing(true) // 使通知不可被用户轻易划掉
            .build()
    }

    private fun updateNotification(contentText: String) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "后台定位服务",
                NotificationManager.IMPORTANCE_LOW // 设置为LOW，避免声音和振动打扰用户
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 当服务被销毁时（无论正常停止还是被系统杀死），向Manager同步状态
        trackingManager.updateTrackingState(false)
        // 取消所有正在运行的协程任务，防止内存泄漏
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START_LOCATION_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_LOCATION_TRACKING"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    }
}