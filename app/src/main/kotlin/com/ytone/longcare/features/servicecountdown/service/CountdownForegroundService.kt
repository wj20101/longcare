package com.ytone.longcare.features.servicecountdown.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownAppLauncher
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 服务倒计时前台服务
 * 负责显示持续的倒计时通知，每秒更新剩余时间
 */
@AndroidEntryPoint
class CountdownForegroundService : Service() {

    @Inject
    lateinit var appLauncher: ServiceCountdownAppLauncher

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
        createForegroundNotificationChannel(
            channelId = FOREGROUND_NOTIFICATION_CHANNEL_ID
        )
        logI("CountdownForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COUNTDOWN -> {
                orderId = intent.getLongExtra(EXTRA_ORDER_ID, 0)
                serviceName = intent.getStringExtra(EXTRA_SERVICE_NAME) ?: ""
                val totalSeconds = intent.getLongExtra(EXTRA_TOTAL_SECONDS, 0)
                
                // 立即启动前台服务，避免超时异常
                val notification = createCountdownNotification(
                    channelId = FOREGROUND_NOTIFICATION_CHANNEL_ID,
                    serviceName = serviceName,
                    orderId = orderId,
                    appLauncher = appLauncher
                )
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
}
