package com.ytone.longcare.features.servicecountdown.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import com.ytone.longcare.model.OrderKey

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

        /**
         * 启动倒计时前台服务
         */
        fun startCountdown(
            context: Context,
            orderKey: OrderKey,
            serviceName: String,
            totalSeconds: Long
        ) {
            val intent = Intent(context, CountdownForegroundService::class.java).apply {
                action = ACTION_START_COUNTDOWN
                putExtra(EXTRA_ORDER_ID, orderKey.orderId)
                putExtra(EXTRA_SERVICE_NAME, serviceName)
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 停止倒计时前台服务
         */
        fun stopCountdown(context: Context) {
            val intent = Intent(context, CountdownForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val binder = Binder()

    // 倒计时状态
    private var orderId: Long = 0
    private var serviceName: String = ""

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
                val totalSeconds = intent.getLongExtra(EXTRA_TOTAL_SECONDS, 0)
                
                // 立即启动前台服务，避免超时异常
                val notification = createCountdownNotification()
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                ServiceCompat.startForeground(
                    this,
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    type
                )
                logI("倒计时前台服务已启动: orderId=$orderId, serviceName=$serviceName, totalSeconds=$totalSeconds")
            }

            ACTION_STOP_COUNTDOWN -> {
                stopCountdownNotification()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        logI("App task removed, stopping CountdownForegroundService")
        stopCountdownNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdownNotification()
        logI("CountdownForegroundService destroyed")
    }

    /**
     * 停止倒计时通知
     */
    private fun stopCountdownNotification() {
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

        val pendingIntent = PendingIntentCompat.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        val builder = NotificationCompat.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.app_logo_round)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        } else {
            logI("CountdownForegroundService: PendingIntentCompat 返回 null，通知点击跳转不可用")
        }

        return builder.build()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            FOREGROUND_NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName("服务倒计时通知")
            .setDescription("显示服务倒计时的实时进度")
            .setVibrationEnabled(false)
            .setLightsEnabled(false)
            .setShowBadge(false)
            .build()

        NotificationManagerCompat.from(this).createNotificationChannel(channel)
        logI("倒计时前台服务通知渠道已创建")
    }

}
