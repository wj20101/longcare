package com.ytone.longcare.features.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 服务时间结束闹钟广播接收器
 * 作为三重保障机制的第一重保障（AlarmManager）
 */
@AndroidEntryPoint
class ServiceTimeAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var serviceTimeNotificationManager: ServiceTimeNotificationManager

    @Inject
    lateinit var pendingOrdersStorage: PendingOrdersStorage

    override fun onReceive(context: Context, intent: Intent) {
        logI("收到服务时间结束闹钟广播")
        
        // 验证Intent Action
        if (intent.action != ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM) {
            logE("收到未知Action的广播: ${intent.action}")
            return
        }
        
        val orderId = intent.getLongExtra(ServiceTimeNotificationManager.EXTRA_ORDER_ID, -1L)
        val serviceName = intent.getStringExtra(ServiceTimeNotificationManager.EXTRA_SERVICE_NAME) ?: "未知服务"
        val serviceEndTime = intent.getLongExtra(ServiceTimeNotificationManager.EXTRA_SERVICE_END_TIME, 0L)
        
        if (orderId == -1L) {
            logE("服务时间结束闹钟广播缺少orderId")
            return
        }

        // 获取WakeLock确保设备唤醒
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LongCare:ServiceTimeEndAlarm"
        )
        
        try {
            wakeLock.acquire(10000) // 持有10秒，足够处理通知
            
            logI("处理服务时间结束通知: orderId=$orderId, serviceName=$serviceName, endTime=$serviceEndTime")
            
            // 显示通知
            serviceTimeNotificationManager.showServiceTimeEndNotification(orderId, serviceName)
            
            // 通知已触发，从存储中移除待处理订单
            pendingOrdersStorage.removePendingOrder(orderId)
            
            logI("服务时间结束通知处理完毕: orderId=$orderId")
            
        } catch (e: Exception) {
            logE("处理服务时间结束闹钟失败: ${e::class.java.simpleName}: ${e.message}")
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
