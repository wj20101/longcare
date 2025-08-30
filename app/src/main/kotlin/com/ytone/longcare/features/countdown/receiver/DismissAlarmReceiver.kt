package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
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
    
    override fun onReceive(context: Context, intent: Intent) {
        logI("DismissAlarmReceiver: 收到关闭响铃广播")
        
        val orderId = intent.getLongExtra(CountdownNotificationManager.EXTRA_ORDER_ID, 0L)
        val serviceName = intent.getStringExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME) ?: ""
        
        // 取消通知
        countdownNotificationManager.cancelCountdownCompletionNotification()
        
        // 发送广播通知其他组件停止响铃
        val stopAlarmIntent = Intent(ACTION_STOP_ALARM).apply {
            setPackage(context.packageName)
            putExtra(CountdownNotificationManager.EXTRA_ORDER_ID, orderId)
            putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
        }
        context.sendBroadcast(stopAlarmIntent)
        
        logI("DismissAlarmReceiver: 响铃已关闭，orderId=$orderId, serviceName=$serviceName")
    }
    
    companion object {
        const val ACTION_STOP_ALARM = "com.ytone.longcare.STOP_ALARM"
    }
}