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
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics
import com.ytone.longcare.features.countdown.manager.CountdownAlarm
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 闹铃响铃服务
 * 负责持续播放闹铃声音和震动，最多 30 秒；用户确认后移除通知
 * 升级为前台服务以确保在后台/锁屏时的优先级
 */
@AndroidEntryPoint
class AlarmRingtoneService : Service() {

    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager

    private lateinit var playbackController: AlarmRingtonePlaybackController
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeAlarm: CountdownAlarm? = null
    private var resourcesCleanedUp = false
    private val ringingHandler = Handler(Looper.getMainLooper())
    private var ringingTimeout: Runnable? = null
    
    companion object {
        internal const val RINGING_TIMEOUT_MS = 30_000L
        internal const val ACTION_START_RINGTONE = "com.ytone.longcare.action.START_RINGTONE"
        
        /**
         * 启动响铃服务
         */
        internal fun startRingtone(context: Context, alarm: CountdownAlarm) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_START_RINGTONE
                putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, alarm.orderKey)
                putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, alarm.serviceName)
                putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, alarm.owner)
                putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, alarm.triggerTimeMillis)
                CountdownAlarmDiagnostics.attach(this, alarm.diagnostics)
            }
            // 使用Compat库确保兼容性，自动处理Android 8.0+的前台服务启动
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * 停止响铃服务
         */
        fun stopRingtone(context: Context) {
            context.stopService(Intent(context, AlarmRingtoneService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        logI("AlarmRingtoneService: 服务创建")
        playbackController = AlarmRingtonePlaybackController(applicationContext)
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
        CountdownAlarmDiagnostics.serviceEntered(CountdownAlarmDiagnostics.read(intent))
        logI("AlarmRingtoneService: 收到启动命令")

        val alarm = countdownNotificationManager.extractAlarm(intent)
        if (alarm == null || !countdownNotificationManager.ownsAlarm(alarm)) {
            logI("AlarmRingtoneService: 忽略已取消或不属于当前会话的提醒")
            alarm?.let { countdownNotificationManager.trackDeliveryResult(it, "service_ownership_rejected") }
            if (activeAlarm == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (activeAlarm?.let { it.orderKey == alarm.orderKey && it.owner == alarm.owner &&
                it.triggerTimeMillis == alarm.triggerTimeMillis } == true && !resourcesCleanedUp) {
            return START_NOT_STICKY
        }
        activeAlarm = alarm
        resourcesCleanedUp = false

        acquireWakeLock()
        
        // 追踪响铃服务启动事件
        CountdownEventTracker.trackEvent(
            eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_START,
            orderId = alarm.orderKey.orderId,
            extras = CountdownAlarmDiagnostics.fields(alarm.diagnostics, alarm.triggerTimeMillis, "service_entered"),
            userId = CountdownAlarmDiagnostics.userId(alarm.diagnostics, alarm.owner)
        )

        // 立即升级为前台服务，显示高优先级通知
        if (!countdownNotificationManager.startAlarmForeground(this, alarm)) {
            stopAlarmAndSelf("foreground_start_failed")
            return START_NOT_STICKY
        }
        
        // 启动响铃和震动
        if (!playbackController.isPlaying && !playbackController.start()) {
            logE("AlarmRingtoneService: 响铃播放启动失败，停止响铃服务")
            countdownNotificationManager.trackDeliveryResult(alarm, "playback_start_failed", playbackController.lastStartFailureType)
            stopAlarmAndSelf("playback_start_failed")
            return START_NOT_STICKY
        }
        
        // This confirms start() returned successfully, not that the user physically heard audio.
        countdownNotificationManager.trackDeliveryResult(alarm, "playback_started")

        // 锁屏弹页仅由系统 fullScreenIntent 处理，权限不足时保留通知供用户点击。
        scheduleRingingTimeout()
        
        return START_NOT_STICKY
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

        cancelRingingTimeout()
        playbackController.stop()
        detachForegroundNotification()
        releaseWakeLock()

        CountdownEventTracker.trackEvent(
            eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_STOP,
            orderId = activeAlarm?.orderKey?.orderId,
            extras = mapOf(
                "serviceName" to activeAlarm?.serviceName,
                "reason" to reason
            ),
            userId = CountdownAlarmDiagnostics.userId(activeAlarm?.diagnostics, activeAlarm?.owner)
        )
    }

    private fun scheduleRingingTimeout() {
        cancelRingingTimeout()
        val timeout = Runnable { stopAlarmAndSelf("ringing_timeout") }
        ringingTimeout = timeout
        ringingHandler.postDelayed(timeout, RINGING_TIMEOUT_MS)
    }

    private fun cancelRingingTimeout() {
        ringingTimeout?.let(ringingHandler::removeCallbacks)
        ringingTimeout = null
    }

    private fun detachForegroundNotification() {
        // 声音结束不等于用户确认；退出前台后仍保留唯一的可点击提醒。
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun acquireWakeLock() {
        try {
            wakeLock?.acquire(RINGING_TIMEOUT_MS + 5_000L /* 留出启动与资源释放预算 */)
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
