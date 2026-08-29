package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownIntentPurpose
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.manager.CountdownTaskExecutionGate
import com.ytone.longcare.features.countdown.manager.CountdownTaskPayload
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 倒计时闹钟广播接收器
 * 处理倒计时完成时的通知、响铃和震动
 */
@AndroidEntryPoint
class CountdownAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskCodec: CountdownTaskCodec

    @Inject
    lateinit var executionGate: CountdownTaskExecutionGate

    override fun onReceive(context: Context, intent: Intent) {
        val payload = taskCodec.fromIntent(intent, CountdownIntentPurpose.ALARM) ?: return
        if (!executionGate.isCurrent(payload)) return
        CountdownAlarmReceiverDelegate.handle(context, payload, taskCodec, executionGate)
    }
}

internal object CountdownAlarmReceiverDelegate {
    private const val WAKE_LOCK_RELEASE_DELAY_MS = 5_000L
    private val wakeLockReleaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun handle(
        context: Context,
        payload: CountdownTaskPayload,
        taskCodec: CountdownTaskCodec,
        executionGate: CountdownTaskExecutionGate,
    ) {
        if (!executionGate.isCurrent(payload)) return
        logI("========================================")
        logI("🔔 收到倒计时闹钟广播")
        logI("========================================")

        val orderKey = payload.orderKey
        val orderId = orderKey.orderId
        val serviceName = payload.serviceName

        logI("📋 订单信息: orderId=$orderId, serviceName=$serviceName")

        // 记录闹钟触发事件（用于问题排查）
        logAlarmTriggerEvent(orderId, serviceName)

        // 获取WakeLock确保设备唤醒
        // 使用 PARTIAL_WAKE_LOCK 保持CPU运行，屏幕点亮由 Activity 的 setTurnScreenOn 处理
        val powerManager = context.getSystemService<PowerManager>()
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LongCare:CountdownAlarm"
        )

        try {
            // 持有30秒WakeLock，确保有足够时间完成所有操作
            wakeLock?.acquire(30000)
            logI("✅ WakeLock已获取")

            // 1. 先停止前台服务，清除进行中的通知
            if (!executionGate.isCurrent(payload)) return
            CountdownForegroundService.stopCountdown(context)
            logI("✅ 倒计时前台服务已停止")

            // 2. 启动响铃服务（持续播放声音和震动，并负责显示全屏通知和启动Activity）
            // 注意：我们将显示UI和播放声音的逻辑全部移交给了AlarmRingtoneService
            // 这样可以通过前台服务获得更高的优先级，解决华为/三星等设备后台无法启动Activity的问题
            if (!executionGate.isCurrent(payload)) return
            AlarmRingtoneService.startRingtone(context, payload, taskCodec)
            logI("✅ 响铃服务已启动")

            logI("========================================")
            logI("✅ 倒计时完成处理完毕 (后续逻辑由Service接管)")
            logI("========================================")
        } catch (e: Exception) {
            logE("========================================")
            logE("❌ 处理倒计时闹钟失败: ${e.message}", throwable = e)
            logE("========================================")
        } finally {
            // 延迟释放WakeLock，确保Activity完全启动
            if (wakeLock?.isHeld == true) {
                wakeLockReleaseScope.launch {
                    delay(WAKE_LOCK_RELEASE_DELAY_MS)
                    runCatching {
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                            logI("✅ WakeLock已释放")
                        }
                    }.onFailure { error ->
                        logE("❌ WakeLock释放失败: ${error.message}", throwable = error)
                    }
                }
            }
        }
    }

    /**
     * 记录闹钟触发事件（用于问题排查）
     * 使用 CountdownEventTracker 进行统一上报
     */
    private fun logAlarmTriggerEvent(orderId: Long, serviceName: String) {
        CountdownEventTracker.trackEvent(
            eventType = CountdownEventTracker.EventType.ALARM_TRIGGERED,
            orderId = orderId,
            extras = mapOf(
                "serviceName" to serviceName,
                "triggerTime" to System.currentTimeMillis()
            )
        )
    }
}
