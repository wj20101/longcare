package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
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

        countdownNotificationManager.dismissAlarm(
            orderKey, intent.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER),
            intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))

        logI("DismissAlarmReceiver: 响铃已关闭，orderId=$orderId, serviceName=$serviceName")
    }
}
