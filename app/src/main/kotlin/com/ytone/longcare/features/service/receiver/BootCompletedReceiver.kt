package com.ytone.longcare.features.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.storage.PendingOrder
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

    @Inject
    lateinit var privacyConsentManager: PrivacyConsentManager

    @Inject
    lateinit var userSessionRepository: UserSessionRepository

    override fun onReceive(context: Context, intent: Intent) {
        logI("收到设备重启完成广播: ${intent.action}")
        
        // 验证广播Action
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && 
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            logE("收到非重启相关的广播: ${intent.action}")
            return
        }

        // 隐私合规：用户未同意隐私政策时不执行任何操作
        if (!privacyConsentManager.isPrivacyConsented) {
            logI("用户未同意隐私政策，跳过通知恢复")
            return
        }

        // 用户未登录时不需要恢复通知
        if (userSessionRepository.sessionState.value !is SessionState.LoggedIn) {
            logI("用户未登录，跳过通知恢复")
            return
        }

        // 无待处理订单时无需启动后台任务
        val pendingOrders = pendingOrdersStorage.getAllPendingOrders()
        if (pendingOrders.isEmpty()) {
            logI("无待处理订单，跳过通知恢复")
            return
        }

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                recoverServiceTimeNotifications(pendingOrders)
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
    private fun recoverServiceTimeNotifications(pendingOrders: List<PendingOrder>) {
        logI("开始恢复服务时间通知任务，共 ${pendingOrders.size} 个订单")
        
        var recoveredCount = 0
        val currentTime = System.currentTimeMillis()
        
        pendingOrders.forEach { order ->
            try {
                if (order.serviceEndTime > currentTime) {
                    serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
                        order.orderId,
                        order.serviceName,
                        order.serviceEndTime
                    )
                    recoveredCount++
                    logI("恢复通知成功: orderId=${order.orderId}, serviceName=${order.serviceName}, endTime=${order.serviceEndTime}")
                } else {
                    pendingOrdersStorage.removePendingOrder(order.orderId)
                    logI("移除过期订单: orderId=${order.orderId}")
                }
            } catch (e: Exception) {
                logE("恢复订单通知失败: orderId=${order.orderId}, error=${e.message}")
            }
        }
        
        logI("服务时间通知任务恢复完成，成功恢复 $recoveredCount 个通知")
    }
}
