package com.ytone.longcare.features.location.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.ytone.longcare.MainActivity
import com.ytone.longcare.R
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.AddPositionParamModel
import com.ytone.longcare.common.utils.LogExt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * 定位跟踪前台服务
 * 负责在后台持续获取用户位置并上报到服务器
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var apiService: LongCareApiService

    // 获取一个在主线程上执行任务的 Executor
    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(this) }

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListenerCompat? = null
    private var serviceJob: Job? = null
    private var orderId: Long = 0L
    private var isTracking = false

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val LOCATION_UPDATE_INTERVAL = 30 * 1000L // 30秒
        private const val LOCATION_UPDATE_MIN_INTERVAL = 15 * 1000L // 15秒
        private const val MIN_DISTANCE_CHANGE = 0f // 最小距离变化

        const val ACTION_START_TRACKING = "start_tracking"
        const val ACTION_STOP_TRACKING = "stop_tracking"
        const val EXTRA_ORDER_ID = "order_id"

        /**
         * 启动定位跟踪服务
         */
        fun startTracking(context: Context, orderId: Long) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_ORDER_ID, orderId)
            }
            ContextCompat.startForegroundService(context,intent)
        }

        /**
         * 停止定位跟踪服务
         */
        fun stopTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogExt.d("LocationTrackingService", "Service created")
        createNotificationChannel()
        initLocationManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                orderId = intent.getLongExtra(EXTRA_ORDER_ID, 0L)
                startLocationTracking()
            }
            ACTION_STOP_TRACKING -> {
                stopLocationTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        LogExt.d("LocationTrackingService", "Service destroyed")
        stopLocationTracking()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        val channel =
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("定位跟踪服务").setDescription("后台定位跟踪服务通知").setShowBadge(false)
                .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    /**
     * 初始化位置管理器
     */
    private fun initLocationManager() {
        locationManager = ContextCompat.getSystemService(this,LocationManager::class.java)
    }

    /**
     * 开始位置跟踪
     */
    private fun startLocationTracking() {
        if (isTracking) {
            LogExt.d("LocationTrackingService", "Already tracking")
            return
        }

        if (!hasLocationPermission()) {
            LogExt.e("LocationTrackingService", "No location permission")
            stopSelf()
            return
        }

        isTracking = true
        startForeground(NOTIFICATION_ID, createNotification())

        // 创建位置监听器
        locationListener = LocationListenerCompat {
            LogExt.d("LocationTrackingService", "Location changed: ${it.latitude}, ${it.longitude}")
            uploadLocation(it)
        }

        // 启动协程作业
        serviceJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            requestLocationUpdates()
        }

        LogExt.d("LocationTrackingService", "Location tracking started for order: $orderId")
    }

    /**
     * 停止位置跟踪
     */
    private fun stopLocationTracking() {
        if (!isTracking) return

        isTracking = false
        serviceJob?.cancel()
        locationListener?.let { listener ->
            try {
                locationManager?.let { manager ->
                    LocationManagerCompat.removeUpdates(
                        manager,
                        listener
                    )
                }
            } catch (e: SecurityException) {
                LogExt.e("LocationTrackingService", "Error removing location updates", e)
            }
        }
        locationListener = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        LogExt.d("LocationTrackingService", "Location tracking stopped")
    }

    /**
     * 请求位置更新
     */
    private suspend fun requestLocationUpdates() = withContext(Dispatchers.Main) {
        try {
            locationManager?.let { manager ->
                locationListener?.let { listener ->
                    val provider = when {
                        LocationManagerCompat.hasProvider(
                            manager,
                            LocationManager.GPS_PROVIDER
                        ) -> LocationManager.GPS_PROVIDER

                        LocationManagerCompat.hasProvider(
                            manager,
                            LocationManager.NETWORK_PROVIDER
                        ) -> LocationManager.NETWORK_PROVIDER

                        else -> return@let
                    }
                    if (ActivityCompat.checkSelfPermission(
                            this@LocationTrackingService,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(
                            this@LocationTrackingService,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val locationRequest = LocationRequestCompat
                            .Builder(LOCATION_UPDATE_INTERVAL) // 设置基础间隔为30秒
                            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY) // 请求高精度
                            .setMinUpdateIntervalMillis(LOCATION_UPDATE_MIN_INTERVAL) // 最快更新间隔（例如15秒），防止过于频繁
                            .setMinUpdateDistanceMeters(MIN_DISTANCE_CHANGE) // 不根据距离更新
                            .build()
                        LocationManagerCompat.requestLocationUpdates(
                            manager,
                            provider,
                            locationRequest,
                            mainThreadExecutor,
                            listener
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LogExt.e("LocationTrackingService", "Error requesting location updates", e)
        }
    }

    /**
     * 上传位置信息到服务器
     */
    private fun uploadLocation(location: Location) {
        if (orderId <= 0) {
            LogExt.e("LocationTrackingService", "Invalid order ID: $orderId")
            return
        }

        // 在IO线程中执行网络请求
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val param = AddPositionParamModel(
                    orderId = orderId,
                    longitude = location.longitude,
                    latitude = location.latitude
                )

                LogExt.d("LocationTrackingService", "Uploading location: ${param.latitude}, ${param.longitude} for order: ${param.orderId}")
                
                val response = apiService.addPosition(param)
                if (response.isSuccess()) {
                    LogExt.d("LocationTrackingService", "Location uploaded successfully")
                } else {
                    LogExt.e("LocationTrackingService", "Failed to upload location: ${response.resultCode} - ${response.resultMsg}")
                }
            } catch (e: Exception) {
                LogExt.e("LocationTrackingService", "Error uploading location", e)
            }
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("定位跟踪服务")
            .setContentText("正在后台跟踪位置信息")
            .setSmallIcon(R.mipmap.app_logo_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 检查是否有位置权限
     */
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}