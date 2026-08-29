package com.ytone.longcare.features.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.ServiceTimeTaskCodec
import com.ytone.longcare.features.service.ServiceTimeTaskExecutionGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ServiceTimeAlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var serviceTimeNotificationManager: ServiceTimeNotificationManager

    @Inject
    lateinit var taskCodec: ServiceTimeTaskCodec

    @Inject
    lateinit var executionGate: ServiceTimeTaskExecutionGate

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM) {
            logE("收到未知Action的广播: ${intent.action}")
            return
        }
        val payload = taskCodec.fromAlarmIntent(intent)
        if (payload == null) {
            logE("服务时间结束闹钟缺少或携带无效的用户任务身份")
            return
        }
        if (!executionGate.isCurrent(payload)) {
            logI("服务时间结束闹钟用户任务身份已过期，静默结束")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LongCare:ServiceTimeEndAlarm",
        )
        wakeLock.acquire(10_000)
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val shown = serviceTimeNotificationManager.handleTriggered(payload)
                logI("服务时间结束闹钟处理完成: orderId=${payload.orderId}, shown=$shown")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logE("处理服务时间结束闹钟失败: ${error.message}", throwable = error)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }
}
