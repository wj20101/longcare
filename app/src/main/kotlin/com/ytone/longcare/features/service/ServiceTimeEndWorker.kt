package com.ytone.longcare.features.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.userstorage.UserStorageLeaseAccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务时间结束通知Worker
 * 作为 AlarmManager 之外的可持久化后台兜底。
 */
@HiltWorker
class ServiceTimeEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val serviceTimeNotificationManager: ServiceTimeNotificationManager,
    private val taskCodec: ServiceTimeTaskCodec,
    private val executionGate: ServiceTimeTaskExecutionGate,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ServiceTimeEndWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            logI("服务时间结束Worker开始执行")
            
            val payload = taskCodec.fromWorkData(inputData)
            if (payload == null) {
                logE("Worker缺少或携带无效的用户任务身份，任务失败")
                return Result.failure()
            }

            if (!executionGate.isCurrent(payload)) {
                logI("Worker用户任务身份已过期，静默结束: task=${payload.execution.taskIdentity.encode()}")
                return Result.success()
            }

            logI("执行服务时间结束通知: orderId=${payload.orderId}")
            dispatchServiceTimeWorkerPayload(payload, executionGate, serviceTimeNotificationManager)
            logI("服务时间结束Worker执行完成: orderId=${payload.orderId}")
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

@Singleton
class ServiceTimeTaskExecutionGate @Inject constructor(
    private val storageRegistry: UserStorageLeaseAccess,
    private val taskCodec: ServiceTimeTaskCodec,
) {
    fun isCurrent(payload: ServiceTimeTaskPayload): Boolean {
        val lease = storageRegistry.currentLeaseOrNull() ?: return false
        return taskCodec.matchesCurrent(payload.execution, lease)
    }
}

internal suspend fun dispatchServiceTimeWorkerPayload(
    payload: ServiceTimeTaskPayload?,
    executionGate: ServiceTimeTaskExecutionGate,
    manager: ServiceTimeNotificationManager,
): Boolean {
    if (payload == null || !executionGate.isCurrent(payload)) return false
    return manager.handleTriggered(payload)
}
