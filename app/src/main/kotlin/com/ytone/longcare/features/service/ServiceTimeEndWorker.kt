package com.ytone.longcare.features.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * 服务时间结束通知Worker
 * 作为三重保障机制的第二重保障（WorkManager）
 */
@HiltWorker
class ServiceTimeEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val serviceTimeNotificationManager: ServiceTimeNotificationManager,
    private val pendingOrdersStorage: PendingOrdersStorage
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ServiceTimeEndWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            logI("服务时间结束Worker开始执行")
            
            // 获取输入数据
            val orderId = inputData.getLong(ServiceTimeNotificationManager.EXTRA_ORDER_ID, -1L)
            val serviceName = inputData.getString(ServiceTimeNotificationManager.EXTRA_SERVICE_NAME) ?: "未知服务"
            
            if (orderId == -1L) {
                logE("Worker缺少订单ID，任务失败")
                return Result.failure()
            }

            logI("执行服务时间结束通知: orderId=$orderId, serviceName=$serviceName")
            
            // 显示通知
            serviceTimeNotificationManager.showServiceTimeEndNotification(orderId, serviceName)
            
            // 通知已触发，从存储中移除待处理订单
            pendingOrdersStorage.removePendingOrder(orderId)
            
            logI("服务时间结束Worker执行成功: orderId=$orderId")
            Result.success()
            
        } catch (e: CancellationException) {
            logI("服务时间结束Worker被取消: ${e.message}")
            throw e
        } catch (e: Exception) {
            logE("服务时间结束Worker执行失败: ${e.message}")
            
            // 检查重试次数
            if (runAttemptCount < ServiceTimeNotificationManager.MAX_RETRY_COUNT) {
                logI("Worker重试中，当前尝试次数: $runAttemptCount")
                Result.retry()
            } else {
                logE("Worker达到最大重试次数，任务失败")
                Result.failure()
            }
        }
    }
}
