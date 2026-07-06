package com.ytone.longcare.features.countdown.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal object AlarmRingtoneActivityVisibilityTracker {
    private val alarmActivityVisible = AtomicBoolean(false)

    fun markVisible(visible: Boolean) {
        alarmActivityVisible.set(visible)
    }

    fun isVisible(): Boolean = alarmActivityVisible.get()
}

/**
 * 闹铃响铃服务
 * 负责持续播放闹铃声音和震动，直到用户手动关闭
 * 升级为前台服务以确保在后台/锁屏时的优先级
 */
@AndroidEntryPoint
class AlarmRingtoneService : Service() {

    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager

    private lateinit var playbackController: AlarmRingtonePlaybackController
    private lateinit var foregroundStarter: AlarmRingtoneForegroundStarter
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastOrderKey: OrderKey? = null
    private var lastServiceName: String = ""
    private var resourcesCleanedUp = false
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private var noVisibleActivityFallback: Runnable? = null
    
    companion object {
        // 通知ID，与CountdownNotificationManager中保持一致
        private const val NOTIFICATION_ID = 2001
        internal const val NO_VISIBLE_ACTIVITY_TIMEOUT_MS = 30_000L
        internal const val ACTION_START_RINGTONE = "com.ytone.longcare.action.START_RINGTONE"
        internal const val ACTION_STOP_RINGTONE = "com.ytone.longcare.action.STOP_RINGTONE"
        
        /**
         * 启动响铃服务
         */
        fun startRingtone(context: Context, orderKey: OrderKey, serviceName: String) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_START_RINGTONE
                putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
                putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
            }
            // 使用Compat库确保兼容性，自动处理Android 8.0+的前台服务启动
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * 停止响铃服务
         */
        fun stopRingtone(context: Context) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_STOP_RINGTONE
            }
            runCatching {
                context.startService(intent)
            }.onFailure { error ->
                logE("AlarmRingtoneService: 发送停止命令失败，回退 stopService - ${error.message}")
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        logI("AlarmRingtoneService: 服务创建")
        playbackController = AlarmRingtonePlaybackController(applicationContext)
        foregroundStarter = AlarmRingtoneForegroundStarter(
            service = this,
            countdownNotificationManager = countdownNotificationManager,
            notificationId = NOTIFICATION_ID
        )
        
        // 初始化WakeLock
        // 使用 PARTIAL_WAKE_LOCK 保持CPU运行，屏幕点亮由 Activity 的 setTurnScreenOn 处理
        val powerManager = getSystemService<PowerManager>()
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LongCare:AlarmRingtoneService"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logI("AlarmRingtoneService: 收到启动命令")

        if (intent?.action == ACTION_STOP_RINGTONE) {
            logI("AlarmRingtoneService: 收到停止命令")
            stopAlarmAndSelf("stop_action")
            return START_NOT_STICKY
        }

        val orderKey = CountdownNotificationManager.extractOrderKey(intent)
        val serviceName = CountdownNotificationManager.extractServiceName(intent, "未知服务")

        if (orderKey.orderId == -1L) {
            logE("AlarmRingtoneService: 启动参数缺少订单信息，停止响铃服务")
            stopAlarmAndSelf("invalid_start_intent")
            return START_NOT_STICKY
        }

        lastOrderKey = orderKey
        lastServiceName = serviceName
        resourcesCleanedUp = false

        acquireWakeLock()
        
        // 追踪响铃服务启动事件
        CountdownEventTracker.trackEvent(
            eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_START,
            orderId = orderKey.orderId,
            extras = mapOf("serviceName" to serviceName)
        )

        // 立即升级为前台服务，显示高优先级通知
        startForegroundWithNotification(orderKey, serviceName)
        
        // 启动响铃和震动
        if (!playbackController.isPlaying && !playbackController.start()) {
            logE("AlarmRingtoneService: 响铃播放启动失败，停止响铃服务")
            stopAlarmAndSelf("playback_start_failed")
            return START_NOT_STICKY
        }
        
        // 尝试从前台服务启动Activity (作为fullScreenIntent的补充)
        // 注意：Android 10+ (API 29) 限制了后台启动Activity，必须申请 SYSTEM_ALERT_WINDOW 权限或满足特定条件
        // 前台服务属于"可见应用"，通常允许启动Activity，但在某些ROM上可能仍受限
        // 我们在startForegroundWithNotification中已经设置了fullScreenIntent，这是官方推荐的做法
        launchAlarmActivityIfPossible(orderKey, serviceName)
        scheduleNoVisibleActivityFallback(orderKey, serviceName)
        
        return START_NOT_STICKY
    }
    
    /**
     * 启动前台服务通知
     */
    private fun startForegroundWithNotification(orderKey: OrderKey, serviceName: String) {
        try {
            foregroundStarter.startAsForeground(orderKey, serviceName)
        } catch (e: Exception) {
            logE("AlarmRingtoneService: ❌ 启动前台服务失败 - ${e.message}", throwable = e)
            
            // 追踪响铃服务错误事件
            CountdownEventTracker.trackError(
                eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_ERROR,
                orderId = orderKey.orderId,
                throwable = e,
                extras = mapOf("serviceName" to serviceName, "stage" to "startForeground")
            )
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanupAlarmResources("on_destroy")
        logI("AlarmRingtoneService: 服务销毁")
        super.onDestroy()
    }

    private fun stopAlarmAndSelf(reason: String) {
        cleanupAlarmResources(reason)
        stopSelf()
    }

    private fun cleanupAlarmResources(reason: String) {
        if (resourcesCleanedUp) {
            return
        }
        resourcesCleanedUp = true

        cancelNoVisibleActivityFallback()
        playbackController.stop()
        removeForegroundNotification()
        releaseWakeLock()

        CountdownEventTracker.trackEvent(
            eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_STOP,
            orderId = lastOrderKey?.orderId,
            extras = mapOf(
                "serviceName" to lastServiceName,
                "reason" to reason
            )
        )
    }

    private fun scheduleNoVisibleActivityFallback(orderKey: OrderKey, serviceName: String) {
        cancelNoVisibleActivityFallback()
        val fallback = Runnable {
            if (!AlarmRingtoneActivityVisibilityTracker.isVisible()) {
                logE("AlarmRingtoneService: 响铃弹窗未保持可见，自动停止响铃")
                stopAlarmAndSelf("no_visible_alarm_activity")
            } else {
                logI("AlarmRingtoneService: 响铃弹窗可见，跳过自动停止检查")
            }
        }
        noVisibleActivityFallback = fallback
        fallbackHandler.postDelayed(fallback, NO_VISIBLE_ACTIVITY_TIMEOUT_MS)
        logI(
            "AlarmRingtoneService: 已安排弹窗可见性兜底检查，orderId=${orderKey.orderId}, " +
                "serviceName=$serviceName, timeout=${NO_VISIBLE_ACTIVITY_TIMEOUT_MS}ms"
        )
    }

    private fun cancelNoVisibleActivityFallback() {
        noVisibleActivityFallback?.let { fallbackHandler.removeCallbacks(it) }
        noVisibleActivityFallback = null
    }

    private fun removeForegroundNotification() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 退出前台服务失败 - ${e.message}", throwable = e)
        }

        try {
            countdownNotificationManager.cancelCountdownCompletionNotification()
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 取消响铃通知失败 - ${e.message}", throwable = e)
        }
    }

    private fun acquireWakeLock() {
        try {
            wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 获取WakeLock失败 - ${e.message}", throwable = e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 释放WakeLock失败 - ${e.message}", throwable = e)
        }
    }
}
