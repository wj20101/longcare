package com.ytone.longcare.features.servicecountdown.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.Locale
import javax.inject.Inject

/**
 * 服务倒计时前台服务
 * 负责显示持续的倒计时通知，每秒更新剩余时间
 */
@AndroidEntryPoint
class CountdownForegroundService : Service() {

    companion object {
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "countdown_foreground_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2002

        // Intent extras
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
        const val EXTRA_TOTAL_SECONDS = "extra_total_seconds"

        // Actions
        const val ACTION_START_COUNTDOWN = "action_start_countdown"
        const val ACTION_STOP_COUNTDOWN = "action_stop_countdown"
        const val ACTION_UPDATE_TIME = "action_update_time"

        /**
         * 启动倒计时前台服务
         */
        fun startCountdown(
            context: Context,
            orderId: Long,
            serviceName: String,
            totalSeconds: Long
        ) {
            val intent = Intent(context, CountdownForegroundService::class.java).apply {
                action = ACTION_START_COUNTDOWN
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_SERVICE_NAME, serviceName)
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 停止倒计时前台服务
         */
        fun stopCountdown(context: Context) {
            val intent = Intent(context, CountdownForegroundService::class.java).apply {
                action = ACTION_STOP_COUNTDOWN
            }
            context.startService(intent)
        }

        /**
         * 更新倒计时时间（已废弃，通知改为静态显示）
         */
        @Deprecated("通知已改为静态显示，不再需要更新时间")
        fun updateTime(
            context: Context,
            remainingSeconds: Long,
            serviceName: String
        ) {
            // 不再执行任何操作
        }
    }

    @Inject
    lateinit var notificationManager: NotificationManager

    private val binder = CountdownBinder()
    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // 倒计时状态
    private var orderId: Long = 0
    private var serviceName: String = ""
    private var totalSeconds: Long = 0
    private var remainingSeconds: Long = 0
    private var isRunning: Boolean = false

    inner class CountdownBinder : Binder() {
        fun getService(): CountdownForegroundService = this@CountdownForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        logI("CountdownForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COUNTDOWN -> {
                orderId = intent.getLongExtra(EXTRA_ORDER_ID, 0)
                serviceName = intent.getStringExtra(EXTRA_SERVICE_NAME) ?: ""
                totalSeconds = intent.getLongExtra(EXTRA_TOTAL_SECONDS, 0)
                remainingSeconds = totalSeconds
                
                // 立即启动前台服务，避免超时异常
                val notification = createCountdownNotification()
                ServiceCompat.startForeground(
                    this,
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                // 然后进行其他初始化
                startCountdownTimer()
            }

            ACTION_STOP_COUNTDOWN -> {
                stopCountdownNotification()
            }

            ACTION_UPDATE_TIME -> {
                // 不再需要更新通知，保持静态显示
                logI("收到更新时间请求，但通知已改为静态显示")
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdownNotification()
        logI("CountdownForegroundService destroyed")
    }

    /**
     * 开始倒计时定时器
     */
    private fun startCountdownTimer() {
        isRunning = true
        logI("倒计时定时器已启动: orderId=$orderId, serviceName=$serviceName, totalSeconds=$totalSeconds")
    }

    /**
     * 停止倒计时通知
     */
    private fun stopCountdownNotification() {
        isRunning = false
        updateJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        logI("倒计时前台服务已停止")
    }





    /**
     * 创建倒计时通知
     */
    private fun createCountdownNotification(): Notification {
        val contentTitle = "服务进行中"
        val contentText = "$serviceName - 正在为您提供服务"

        // 点击通知跳转到主页面
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("orderId", orderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.app_logo_round)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                "服务倒计时通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示服务倒计时的实时进度"
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
            logI("倒计时前台服务通知渠道已创建")
        }
    }

    /**
     * 格式化时间显示
     * @param seconds 秒数
     * @return 格式化的时间字符串 (HH:mm:ss)
     */
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }
}