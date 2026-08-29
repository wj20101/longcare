package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.manager.CountdownIntentPurpose
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.manager.CountdownTaskExecutionGate
import com.ytone.longcare.features.countdown.manager.CountdownTaskPayload
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 关闭响铃广播接收器
 * 用于处理用户从通知栏关闭响铃的操作
 */
@AndroidEntryPoint
class DismissAlarmReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager

    @Inject
    lateinit var taskCodec: CountdownTaskCodec

    @Inject
    lateinit var executionGate: CountdownTaskExecutionGate
    
    override fun onReceive(context: Context, intent: Intent) {
        val payload = taskCodec.fromIntent(intent, CountdownIntentPurpose.DISMISS) ?: return
        if (!executionGate.isCurrent(payload)) return
        DismissAlarmReceiverDelegate.handle(
            context,
            payload,
            countdownNotificationManager,
            taskCodec,
            executionGate,
        )
    }
    
    companion object {
        const val ACTION_STOP_ALARM = "com.ytone.longcare.STOP_ALARM"
    }
}

internal object DismissAlarmReceiverDelegate {

    fun handle(
        context: Context,
        payload: CountdownTaskPayload,
        countdownNotificationManager: CountdownNotificationManager,
        taskCodec: CountdownTaskCodec,
        executionGate: CountdownTaskExecutionGate,
    ) {
        if (!executionGate.isCurrent(payload)) return
        logI("DismissAlarmReceiver: 收到关闭响铃广播")

        val orderKey = payload.orderKey
        val orderId = orderKey.orderId
        val serviceName = payload.serviceName

        // 停止响铃服务
        AlarmRingtoneService.stopRingtone(context)

        // 取消通知
        countdownNotificationManager.dismiss(payload)

        // 发送广播通知其他组件停止响铃
        val stopAlarmIntent = taskCodec.writeToIntent(
            Intent(DismissAlarmReceiver.ACTION_STOP_ALARM).setPackage(context.packageName),
            payload,
            CountdownIntentPurpose.DISMISS,
        )
        context.sendBroadcast(stopAlarmIntent)

        logI("DismissAlarmReceiver: 响铃已关闭，orderId=$orderId, serviceName=$serviceName")
    }
}
