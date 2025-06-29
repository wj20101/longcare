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
import android.os.Bundle
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var locationManager: LocationManager? = null
    private var gpsLocationListener: LocationListenerCompat? = null
    private var networkLocationListener: LocationListenerCompat? = null
    private var serviceJob: Job? = null
    private var orderId: Long = 0L
    private var isTracking = false
    private var lastUploadTime = 0L
    private var lastLocation: Location? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val LOCATION_UPDATE_INTERVAL = 30 * 1000L // 30秒
        private const val MIN_DISTANCE_CHANGE = 0f // 最小距离变化
        private const val MIN_UPLOAD_INTERVAL = 25 * 1000L // 最小上报间隔25秒，避免重复上报
        private const val MIN_LOCATION_ACCURACY = 100f // 最小位置精度100米

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
            ContextCompat.startForegroundService(context, intent)
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
        LogExt.d("LocationTrackingService", "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                orderId = intent.getLongExtra(EXTRA_ORDER_ID, 0L)
                LogExt.d("LocationTrackingService", "Starting tracking for order: $orderId")
                startLocationTracking()
            }
            ACTION_STOP_TRACKING -> {
                LogExt.d("LocationTrackingService", "Stopping tracking")
                stopLocationTracking()
            }
            else -> {
                LogExt.w("LocationTrackingService", "Unknown action: ${intent?.action}")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        LogExt.d("LocationTrackingService", "Service destroyed")
        stopLocationTracking()
        serviceScope.cancel()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID, 
            NotificationManagerCompat.IMPORTANCE_HIGH // 提高重要性
        )
            .setName("定位跟踪服务")
            .setDescription("后台定位跟踪服务通知，确保服务持续运行")
            .setShowBadge(true) // 显示角标
            .setLightsEnabled(false) // 关闭指示灯
            .setVibrationEnabled(false) // 关闭震动
            .setSound(null, null) // 关闭声音
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
        LogExt.d("LocationTrackingService", "startLocationTracking called, isTracking: $isTracking")
        
        if (isTracking) {
            LogExt.d("LocationTrackingService", "Already tracking")
            return
        }

        if (orderId <= 0) {
            LogExt.e("LocationTrackingService", "Invalid order ID: $orderId, stopping service")
            stopSelf()
            return
        }

        if (!hasLocationPermission()) {
            LogExt.e("LocationTrackingService", "No location permission, stopping service")
            stopSelf()
            return
        }

        if (!isLocationEnabled()) {
            LogExt.e("LocationTrackingService", "Location service is disabled, stopping service")
            stopSelf()
            return
        }

        try {
            LogExt.d("LocationTrackingService", "Starting foreground service with notification")
            isTracking = true
            startForeground(NOTIFICATION_ID, createNotification())

            // 创建GPS位置监听器
            gpsLocationListener = object : LocationListenerCompat {
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
                    super.onStatusChanged(provider, status, extras)
                    LogExt.d("LocationTrackingService", "GPS Status Changed: $provider, $status, $extras")
                }

                override fun onProviderDisabled(provider: String) {
                    super.onProviderDisabled(provider)
                    LogExt.d("LocationTrackingService", "GPS Provider Disabled: $provider")
                }

                override fun onProviderEnabled(provider: String) {
                    super.onProviderEnabled(provider)
                    LogExt.d("LocationTrackingService", "GPS Provider Enabled: $provider")
                }

                override fun onLocationChanged(location: Location) {
                    LogExt.d("LocationTrackingService", "GPS Location changed: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")
                    handleLocationUpdate(location, "GPS")
                }
            }

            // 创建网络位置监听器
            networkLocationListener = object : LocationListenerCompat {
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
                    super.onStatusChanged(provider, status, extras)
                    LogExt.d("LocationTrackingService", "Network Status Changed: $provider, $status, $extras")
                }

                override fun onProviderDisabled(provider: String) {
                    super.onProviderDisabled(provider)
                    LogExt.d("LocationTrackingService", "Network Provider Disabled: $provider")
                }

                override fun onProviderEnabled(provider: String) {
                    super.onProviderEnabled(provider)
                    LogExt.d("LocationTrackingService", "Network Provider Enabled: $provider")
                }

                override fun onLocationChanged(location: Location) {
                    LogExt.d("LocationTrackingService", "Network Location changed: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")
                    handleLocationUpdate(location, "Network")
                }
            }

            // 启动协程作业
            serviceJob = serviceScope.launch {
                LogExt.d("LocationTrackingService", "Starting location updates coroutine")
                requestLocationUpdates()
            }

            LogExt.d("LocationTrackingService", "Location tracking started successfully for order: $orderId")
        } catch (e: Exception) {
            LogExt.e("LocationTrackingService", "Failed to start location tracking", e)
            isTracking = false
            stopSelf()
        }
    }

    /**
     * 停止位置跟踪
     */
    private fun stopLocationTracking() {
        if (!isTracking) return

        isTracking = false
        serviceJob?.cancel()
        
        // 移除GPS监听器
        gpsLocationListener?.let { listener ->
            try {
                locationManager?.let { manager ->
                    LocationManagerCompat.removeUpdates(manager, listener)
                }
            } catch (e: SecurityException) {
                LogExt.e("LocationTrackingService", "Error removing GPS location updates", e)
            }
        }
        gpsLocationListener = null
        
        // 移除网络监听器
        networkLocationListener?.let { listener ->
            try {
                locationManager?.let { manager ->
                    LocationManagerCompat.removeUpdates(manager, listener)
                }
            } catch (e: SecurityException) {
                LogExt.e("LocationTrackingService", "Error removing Network location updates", e)
            }
        }
        networkLocationListener = null
        
        // 重置状态
        lastUploadTime = 0L
        lastLocation = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        LogExt.d("LocationTrackingService", "Location tracking stopped")
    }

    /**
     * 请求位置更新
     */
    private suspend fun requestLocationUpdates() = withContext(Dispatchers.Main) {
        try {
            LogExt.d("LocationTrackingService", "requestLocationUpdates called")
            
            if (!isLocationEnabled()) {
                LogExt.e("LocationTrackingService", "Location service is disabled, cannot request updates")
                return@withContext
            }

            val manager = locationManager
            val gpsListener = gpsLocationListener
            val networkListener = networkLocationListener
            
            if (manager == null) {
                LogExt.e("LocationTrackingService", "LocationManager is null")
                return@withContext
            }
            
            if (gpsListener == null || networkListener == null) {
                LogExt.e("LocationTrackingService", "LocationListeners are null")
                return@withContext
            }
            
            if (!hasLocationPermission()) {
                LogExt.e("LocationTrackingService", "No location permission for requesting updates")
                return@withContext
            }
            
            val locationRequest = LocationRequestCompat
                .Builder(LOCATION_UPDATE_INTERVAL) // 设置基础间隔为30秒
                .setMinUpdateDistanceMeters(MIN_DISTANCE_CHANGE) // 不根据距离更新
                .build()
            
            var providersRegistered = 0
            
            // 注册GPS提供者
            if (LocationManagerCompat.hasProvider(manager, LocationManager.GPS_PROVIDER)) {
                try {
                    LocationManagerCompat.requestLocationUpdates(
                        manager,
                        LocationManager.GPS_PROVIDER,
                        locationRequest,
                        mainThreadExecutor,
                        gpsListener
                    )
                    LogExt.d("LocationTrackingService", "GPS provider registered successfully")
                    providersRegistered++
                } catch (e: Exception) {
                    LogExt.e("LocationTrackingService", "Failed to register GPS provider", e)
                }
            } else {
                LogExt.w("LocationTrackingService", "GPS provider not available")
            }
            
            // 注册网络提供者
            if (LocationManagerCompat.hasProvider(manager, LocationManager.NETWORK_PROVIDER)) {
                try {
                    LocationManagerCompat.requestLocationUpdates(
                        manager,
                        LocationManager.NETWORK_PROVIDER,
                        locationRequest,
                        mainThreadExecutor,
                        networkListener
                    )
                    LogExt.d("LocationTrackingService", "Network provider registered successfully")
                    providersRegistered++
                } catch (e: Exception) {
                    LogExt.e("LocationTrackingService", "Failed to register Network provider", e)
                }
            } else {
                LogExt.w("LocationTrackingService", "Network provider not available")
            }
            
            if (providersRegistered == 0) {
                LogExt.e("LocationTrackingService", "No location providers could be registered")
                return@withContext
            }
            
            LogExt.d("LocationTrackingService", "Location updates requested successfully with $providersRegistered providers")
            
        } catch (e: SecurityException) {
            LogExt.e("LocationTrackingService", "Security exception when requesting location updates", e)
        } catch (e: Exception) {
            LogExt.e("LocationTrackingService", "Error requesting location updates", e)
        }
    }

    /**
     * 处理位置更新（去重逻辑）
     */
    private fun handleLocationUpdate(location: Location, provider: String) {
        val currentTime = System.currentTimeMillis()
        
        // 检查位置精度
        if (location.accuracy > MIN_LOCATION_ACCURACY) {
            LogExt.d("LocationTrackingService", "Location accuracy too low: ${location.accuracy}m from $provider, ignoring")
            return
        }
        
        // 检查时间间隔，避免频繁上报
        if (currentTime - lastUploadTime < MIN_UPLOAD_INTERVAL) {
            LogExt.d("LocationTrackingService", "Upload interval too short, ignoring location from $provider")
            return
        }
        
        // 检查位置是否有显著变化
        lastLocation?.let { last ->
            val distance = location.distanceTo(last)
            if (distance < MIN_DISTANCE_CHANGE && location.accuracy >= last.accuracy) {
                LogExt.d("LocationTrackingService", "Location change too small: ${distance}m from $provider, ignoring")
                return
            }
        }
        
        // 更新最后上报时间和位置
        lastUploadTime = currentTime
        lastLocation = location
        
        LogExt.d("LocationTrackingService", "Accepting location from $provider: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
        uploadLocation(location)
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
        serviceScope.launch {
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建停止服务的PendingIntent
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("定位跟踪服务")
            .setContentText("正在后台跟踪位置信息，订单ID: $orderId")
            .setSmallIcon(R.mipmap.app_logo_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 提高优先级
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(false) // 不允许用户取消
            .setOnlyAlertOnce(true) // 只提醒一次
            .addAction(
                 android.R.drawable.ic_menu_close_clear_cancel,
                 "停止跟踪",
                 stopPendingIntent
             )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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

    /**
     * 检查位置服务是否启用
     */
    private fun isLocationEnabled(): Boolean {
        return locationManager?.let { manager ->
            LocationManagerCompat.isLocationEnabled(manager)
        } ?: false
    }
}