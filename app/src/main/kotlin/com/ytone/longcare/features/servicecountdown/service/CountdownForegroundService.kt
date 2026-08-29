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
import com.ytone.longcare.features.countdown.manager.CountdownIntentPurpose
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.manager.CountdownTaskExecutionGate
import com.ytone.longcare.features.countdown.manager.CountdownTaskPayload
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

    @Inject
    lateinit var taskCodec: CountdownTaskCodec

    @Inject
    lateinit var executionGate: CountdownTaskExecutionGate

    companion object {
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "countdown_foreground_channel"
        const val EXTRA_TOTAL_SECONDS = "extra_total_seconds"

        // Actions
        const val ACTION_START_COUNTDOWN = "action_start_countdown"
        const val ACTION_STOP_COUNTDOWN = "action_stop_countdown"

        /**
         * 启动倒计时前台服务
         */
        fun startCountdown(
            context: Context,
            payload: CountdownTaskPayload,
            totalSeconds: Long,
            codec: CountdownTaskCodec,
        ) {
            val intent = codec.writeToIntent(
                Intent(context, CountdownForegroundService::class.java).apply {
                    action = ACTION_START_COUNTDOWN
                    putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                },
                payload,
                CountdownIntentPurpose.COUNTDOWN_SERVICE,
            )
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
    private var activePayload: CountdownTaskPayload? = null

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
                val payload = taskCodec.fromIntent(
                    intent,
                    CountdownIntentPurpose.COUNTDOWN_SERVICE,
                )
                if (payload == null || !executionGate.isCurrent(payload)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                activePayload = payload
                val totalSeconds = intent.getLongExtra(EXTRA_TOTAL_SECONDS, 0)
                
                // 立即启动前台服务，避免超时异常
                val notification = createCountdownNotification(
                    channelId = FOREGROUND_NOTIFICATION_CHANNEL_ID,
                    payload = payload,
                    appLauncher = appLauncher,
                    taskCodec = taskCodec,
                )
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                ServiceCompat.startForeground(
                    this,
                    taskCodec.foregroundNotificationId(payload.execution.taskIdentity),
                    notification,
                    type
                )
                logI(
                    "倒计时前台服务已启动: orderId=${payload.orderKey.orderId}, " +
                        "serviceName=${payload.serviceName}, totalSeconds=$totalSeconds",
                )
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
