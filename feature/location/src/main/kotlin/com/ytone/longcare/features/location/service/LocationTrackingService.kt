package com.ytone.longcare.features.location.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var continuousAmapLocationManager: ContinuousAmapLocationManager

    private var isKeepAliveStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logI("📥 收到Intent: action=${intent?.action}")

        when (intent?.action) {
            ACTION_ACQUIRE_KEEP_ALIVE -> {
                val owner = intent.getStringExtra(EXTRA_OWNER) ?: "anonymous"
                startKeepAlive(owner)
            }

            ACTION_RELEASE_KEEP_ALIVE -> {
                stopKeepAlive()
            }

            else -> {
                logI("📥 收到未知命令: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun startKeepAlive(owner: String) {
        if (isKeepAliveStarted) {
            logI("定位保活服务已运行，跳过重复启动 (owner=$owner)")
            return
        }

        try {
            logI("启动定位前台保活 (owner=$owner)")
            createNotificationChannel()
            val notification = createNotification("后台定位服务运行中...")
            startForeground(NOTIFICATION_ID, notification)
            continuousAmapLocationManager.enableBackgroundLocation(NOTIFICATION_ID, notification)
            isKeepAliveStarted = true
        } catch (e: Exception) {
            isKeepAliveStarted = false
            logE("启动定位前台保活失败: ${e.message}")
            stopSelf()
        }
    }

    private fun stopKeepAlive() {
        if (!isKeepAliveStarted) {
            stopSelf()
            return
        }

        try {
            continuousAmapLocationManager.disableBackgroundLocation(true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            logI("定位前台保活已停止")
        } catch (e: Exception) {
            logE("停止定位前台保活失败: ${e.message}")
        } finally {
            isKeepAliveStarted = false
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("后台定位服务").setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName("后台定位服务")
            .setDescription("用于维持定位服务在后台稳定运行")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    /**
     * 当用户从最近任务中滑掉应用时调用
     * 确保服务随应用进程一起停止
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopKeepAlive()
    }

    override fun onDestroy() {
        super.onDestroy()
        isKeepAliveStarted = false
        logI("✅ LocationTrackingService 已销毁")
    }


    companion object {
        const val ACTION_ACQUIRE_KEEP_ALIVE = "ACTION_ACQUIRE_LOCATION_KEEPALIVE"
        const val ACTION_RELEASE_KEEP_ALIVE = "ACTION_RELEASE_LOCATION_KEEPALIVE"
        const val EXTRA_OWNER = "EXTRA_OWNER"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    }
}
