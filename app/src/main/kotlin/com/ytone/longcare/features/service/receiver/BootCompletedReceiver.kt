package com.ytone.longcare.features.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.di.ApplicationScope
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备重启完成广播接收器
 * 用于在服务时间结束通知系统重启后恢复未完成的通知任务
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var serviceTimeNotificationManager: ServiceTimeNotificationManager

    @Inject
    lateinit var pendingOrdersStorage: PendingOrdersStorage

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        logI("收到设备重启完成广播: ${intent.action}")
        
        // 验证广播Action
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && 
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            logE("收到非重启相关的广播: ${intent.action}")
            return
        }

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                recoverServiceTimeNotifications()
                logI("设备重启后通知恢复任务已完成")
            } catch (e: Exception) {
                logE("恢复服务时间通知失败: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 恢复服务时间通知
     * 从持久化存储中读取未完成的通知任务并重新调度
     */
    private fun recoverServiceTimeNotifications() {
        logI("开始恢复服务时间通知任务...")
        
        try {
            // 获取所有待处理的订单
            val pendingOrders = pendingOrdersStorage.getAllPendingOrders()
            logI("找到 ${pendingOrders.size} 个待处理的订单")
            
            var recoveredCount = 0
            val currentTime = System.currentTimeMillis()
            
            // 重新调度每个未完成的通知
            pendingOrders.forEach { order ->
                try {
                    // 只恢复未来的通知
                    if (order.serviceEndTime > currentTime) {
                        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
                            order.orderId,
                            order.serviceName,
                            order.serviceEndTime
                        )
                        recoveredCount++
                        logI("恢复通知成功: orderId=${order.orderId}, serviceName=${order.serviceName}, endTime=${order.serviceEndTime}")
                    } else {
                        // 过期的订单，从存储中移除
                        pendingOrdersStorage.removePendingOrder(order.orderId)
                        logI("移除过期订单: orderId=${order.orderId}")
                    }
                } catch (e: Exception) {
                    logE("恢复订单通知失败: orderId=${order.orderId}, error=${e.message}")
                }
            }
            
            logI("服务时间通知任务恢复完成，成功恢复 $recoveredCount 个通知")
            
        } catch (e: Exception) {
            logE("恢复服务时间通知时发生错误: ${e.message}")
        }
    }
}
