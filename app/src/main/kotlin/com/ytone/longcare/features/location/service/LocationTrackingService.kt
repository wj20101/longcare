package com.ytone.longcare.features.location.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import com.ytone.longcare.features.location.provider.CompositeLocationProvider
import com.ytone.longcare.features.location.provider.LocationResult
import com.ytone.longcare.features.location.provider.LocationStrategy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
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
    
    @Inject
    lateinit var compositeLocationProvider: CompositeLocationProvider

    @Inject
    lateinit var toastHelper: ToastHelper

    // 创建一个与 Service 生命周期绑定的协程作用域
    // 使用 SupervisorJob 确保一个子任务的失败不会影响其他任务
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    private var currentOrderId: Long = INVALID_ORDER_ID

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 服务被创建时，立即向 Manager 同步状态，确保UI能感知到服务正在运行
        trackingManager.updateTrackingState(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val orderId = intent.getLongExtra(EXTRA_ORDER_ID, INVALID_ORDER_ID)
                if (orderId == INVALID_ORDER_ID) {
                    logE("启动服务失败：未提供有效的 orderId。")
                    stopTracking() // 如果没有 orderId，则不启动并立即停止
                } else {
                    currentOrderId = orderId
                    startTracking()
                }
            }
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
     * 获取当前位置的方法，使用新的定位提供者系统
     */
    private suspend fun fetchCurrentLocation() {
        try {
            logI("开始获取位置...")
            val locationResult = compositeLocationProvider.getCurrentLocation()
            
            if (locationResult != null) {
                handleLocationUpdate(locationResult)
            } else {
                logE("所有定位方式都获取位置失败")
                updateNotification("获取位置失败，请检查网络和GPS信号。")
            }
        } catch (e: Exception) {
            logE("定位过程中发生异常: ${e.message}")
            updateNotification("定位异常: ${e.message}")
        }
    }

    /**
     * 处理位置更新和上报的统一方法。
     */
    private fun handleLocationUpdate(locationResult: LocationResult) {
        logI("成功获取到位置: Provider=${locationResult.provider}, Lat=${locationResult.latitude}, Lng=${locationResult.longitude}")
        updateNotification("位置已更新")

        // 在IO线程中执行网络请求
        serviceScope.launch {
            when (val result = locationRepository.addPosition(currentOrderId, locationResult.latitude, locationResult.longitude)) {
                is ApiResult.Success -> {
                    // 请求成功，更新状态
                    logI("位置上报API调用完成。")
                }

                is ApiResult.Failure -> {
                    // 这里可以添加错误处理逻辑，例如通过另一个 StateFlow 显示Toast
                    toastHelper.showShort(result.message)
                }

                is ApiResult.Exception -> {
                    logE(message = "定位上报接口失败", throwable = result.exception)
                }
            }
        }
    }

    private fun stopTracking() {
        logI("停止定位上报循环...")
        trackingJob?.cancel()
        trackingJob = null
        currentOrderId = INVALID_ORDER_ID // 重置 orderId
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
        // 销毁定位提供者资源
        compositeLocationProvider.destroy()
        // 取消所有正在运行的协程任务，防止内存泄漏
        serviceScope.cancel()
    }

    /**
     * 设置定位策略
     */
    fun setLocationStrategy(strategy: LocationStrategy) {
        compositeLocationProvider.setLocationStrategy(strategy)
        logI("定位服务策略已更新为: $strategy")
    }
    
    /**
     * 获取当前定位策略
     */
    fun getCurrentLocationStrategy(): LocationStrategy {
        return compositeLocationProvider.getCurrentStrategy()
    }

    companion object {
        const val ACTION_START = "ACTION_START_LOCATION_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_LOCATION_TRACKING"
        const val EXTRA_ORDER_ID = "EXTRA_ORDER_ID"
        private const val INVALID_ORDER_ID = -1L
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    }
}