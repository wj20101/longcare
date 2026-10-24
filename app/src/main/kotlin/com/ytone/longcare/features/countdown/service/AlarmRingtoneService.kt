package com.ytone.longcare.features.countdown.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics
import com.ytone.longcare.features.countdown.manager.CountdownAlarm
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong
import com.ytone.longcare.features.countdown.service.AlarmRingtonePlaybackController.PlaybackEvent

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
    private var createdAt: CountdownAlarmDiagnostics.ClockSample? = null
    private var activeAlarm: CountdownAlarm? = null
    private var resourcesCleanedUp = false
    private val ringingHandler = Handler(Looper.getMainLooper())
    private var ringingTimeout: Runnable? = null
    private var preparationTimeout: Runnable? = null
    private val playbackGeneration = AtomicLong()
    private var playAuthorized = false
    
    companion object {
        internal const val RINGING_TIMEOUT_MS = 30_000L
        internal const val PREPARATION_TIMEOUT_MS = 5_000L
        internal const val TOTAL_TIMEOUT_MS = PREPARATION_TIMEOUT_MS + RINGING_TIMEOUT_MS
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
            // Android 9 can silently reject a background-restricted FGS start by returning
            // null. AndroidX discards that nullable result, so use the equivalent platform API.
            val component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            checkNotNull(component) {
                "Alarm ringtone foreground service start was rejected"
            }
        }
        
        /**
         * 停止响铃服务
         */
        fun stopRingtone(context: Context) {
            context.stopService(Intent(context, AlarmRingtoneService::class.java))
        }
    }

    override fun onCreate() {
        createdAt = CountdownAlarmDiagnostics.ClockSample.capture()
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
        CountdownAlarmDiagnostics.serviceEntered(CountdownAlarmDiagnostics.read(intent), createdAt)
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
        if (activeAlarm != null && !resourcesCleanedUp) cleanupAlarmResources("replaced")
        activeAlarm = alarm
        resourcesCleanedUp = false
        playAuthorized = false
        val generation = playbackGeneration.incrementAndGet()
        val preparationDeadline = SystemClock.elapsedRealtime() + PREPARATION_TIMEOUT_MS
        val playbackDeadline = preparationDeadline + RINGING_TIMEOUT_MS

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
        
        // Both watchdogs are armed before any potentially blocking media work is dispatched.
        scheduleRingingTimeout(playbackDeadline, "playback_total_timeout")
        preparationTimeout = Runnable {
            if (isActive(generation, alarm)) {
                capturePlaybackProgress(alarm)
                countdownNotificationManager.trackDeliveryResult(alarm, "playback_prepare_timeout")
                stopAlarmAndSelf("playback_prepare_timeout")
                // No play was authorized. Invalidate first so even a racing prepared callback
                // cannot start; an older stuck native start can still make audible fallback unsafe.
                if (!playAuthorized && playbackController.isAudibleFallbackSafe) {
                    countdownNotificationManager.publishPlaybackFallback(alarm)
                }
            }
        }.also { ringingHandler.postDelayed(it, (preparationDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)) }
        // Capture app-scoped manager + atomic token, not the Service, in the worker-side gate.
        val generationState = playbackGeneration
        val manager = countdownNotificationManager
        playbackController.prepare(preparationDeadline, playbackDeadline,
            isCurrent = { generationState.get() == generation && manager.ownsAlarm(alarm) },
            onPrepared = { token ->
                if (isActive(generation, alarm)) {
                    CountdownAlarmDiagnostics.playbackPrepared(alarm.diagnostics, playbackController.preparedAt(token))
                    if (!manager.ownsAlarm(alarm)) {
                        stopAlarmAndSelf("playback_ownership_rejected")
                    } else if (SystemClock.elapsedRealtime() >= preparationDeadline) {
                        preparationTimeout?.run()
                    } else if (playbackController.play(token)) {
                        // Only a successfully accepted token ends preparation. A boundary rejection
                        // keeps the original watchdog (and any queued actual failure) intact.
                        cancelPreparationTimeout()
                        playAuthorized = true
                    }
                }
            },
            onEvent = { event ->
                if (isActive(generation, alarm)) {
                    if (!manager.ownsAlarm(alarm)) {
                        stopAlarmAndSelf("playback_ownership_rejected")
                    } else when (event) {
                        is PlaybackEvent.Started -> {
                            CountdownAlarmDiagnostics.playbackStarted(alarm.diagnostics, event.nativeStartedAt)
                            // Native start returned successfully; this is not proof of audible sound.
                            manager.trackDeliveryResult(alarm, "playback_started",
                                playbackStartupDurationMs = event.startupDurationMs)
                            scheduleRingingTimeout(minOf(playbackDeadline,
                                event.startedAtElapsedMs + RINGING_TIMEOUT_MS), "ringing_timeout")
                        }
                        is PlaybackEvent.Failed -> {
                            manager.trackDeliveryResult(alarm, event.outcome, event.failureType)
                            stopAlarmAndSelf(event.outcome)
                            if ((!playAuthorized || event.releaseSucceeded) && playbackController.isAudibleFallbackSafe) {
                                manager.publishPlaybackFallback(alarm)
                            }
                        }
                        PlaybackEvent.Finished -> stopAlarmAndSelf("ringing_timeout")
                    }
                }
            })

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
        activeAlarm?.let(::capturePlaybackProgress)
        resourcesCleanedUp = true
        playbackGeneration.incrementAndGet()

        cancelPreparationTimeout()
        cancelRingingTimeout()
        playbackController.stop()
        detachForegroundNotification()
        releaseWakeLock()

        CountdownEventTracker.trackEvent(
            eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_STOP,
            orderId = activeAlarm?.orderKey?.orderId,
            extras = CountdownAlarmDiagnostics.fields(activeAlarm?.diagnostics, activeAlarm?.triggerTimeMillis ?: 0L, reason) + mapOf(
                "serviceName" to activeAlarm?.serviceName,
                "reason" to reason
            ),
            userId = CountdownAlarmDiagnostics.userId(activeAlarm?.diagnostics, activeAlarm?.owner)
        )
    }

    private fun capturePlaybackProgress(alarm: CountdownAlarm) {
        playbackController.timingSnapshot()?.let {
            CountdownAlarmDiagnostics.playbackProgress(alarm.diagnostics, it.preparedAt, it.nativeStartedAt)
        }
    }

    private fun isActive(generation: Long, alarm: CountdownAlarm): Boolean =
        !resourcesCleanedUp && playbackGeneration.get() == generation && activeAlarm === alarm

    private fun scheduleRingingTimeout(deadlineElapsedMs: Long, reason: String) {
        cancelRingingTimeout()
        val timeout = Runnable { stopAlarmAndSelf(reason) }
        ringingTimeout = timeout
        ringingHandler.postDelayed(timeout, (deadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
    }

    private fun cancelPreparationTimeout() {
        preparationTimeout?.let(ringingHandler::removeCallbacks)
        preparationTimeout = null
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
            wakeLock?.acquire(TOTAL_TIMEOUT_MS /* 包含准备与播放预算 */)
            // isHeld is only the app's bookkeeping; OEM power services can still ignore/release it.
            CountdownAlarmDiagnostics.wakeLockAttempted(activeAlarm?.diagnostics, wakeLock?.isHeld)
        } catch (e: Exception) {
            CountdownAlarmDiagnostics.wakeLockAttempted(activeAlarm?.diagnostics, null, e.javaClass.simpleName)
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
