package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ytone.longcare.model.OrderKey

/**
 * 关闭响铃广播接收器
 * 用于处理用户从通知栏关闭响铃的操作
 */
@AndroidEntryPoint
class DismissAlarmReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager
    
    override fun onReceive(context: Context, intent: Intent) {
        DismissAlarmReceiverDelegate.handle(context, intent, countdownNotificationManager)
    }
    
    companion object {
        const val ACTION_STOP_ALARM = "com.ytone.longcare.STOP_ALARM"
    }
}

internal object DismissAlarmReceiverDelegate {

    fun handle(
        context: Context,
        intent: Intent,
        countdownNotificationManager: CountdownNotificationManager
    ) {
        logI("DismissAlarmReceiver: 收到关闭响铃广播")

        val orderKey =
            CountdownNotificationManager.extractOrderKey(
                intent,
                defaultValue = OrderKey(orderId = 0L, planId = 0)
            )

        val orderId = orderKey.orderId
        val serviceName = CountdownNotificationManager.extractServiceName(intent, "")

        // 停止响铃服务
        AlarmRingtoneService.stopRingtone(context)

        // 取消通知
        countdownNotificationManager.cancelCountdownCompletionNotification()

        // 发送广播通知其他组件停止响铃
        val stopAlarmIntent = Intent(DismissAlarmReceiver.ACTION_STOP_ALARM).apply {
            setPackage(context.packageName)
            putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
            putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
        }
        context.sendBroadcast(stopAlarmIntent)

        logI("DismissAlarmReceiver: 响铃已关闭，orderId=$orderId, serviceName=$serviceName")
    }
}
