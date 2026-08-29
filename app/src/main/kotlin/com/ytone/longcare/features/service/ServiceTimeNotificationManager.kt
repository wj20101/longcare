package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.userstorage.PendingServiceReminder
import com.ytone.longcare.domain.userstorage.SERVICE_TIME_END_TASK_TYPE
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserServiceReminderRepository
import com.ytone.longcare.domain.userstorage.UserStorageLeaseAccess
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session-scoped Alarm/Work/notification coordinator backed only by the current user's Room. */
@Singleton
class ServiceTimeNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager,
    private val workManager: WorkManager,
    private val reminderRepository: UserServiceReminderRepository,
    private val storageRegistry: UserStorageLeaseAccess,
    private val taskCodec: ServiceTimeTaskCodec,
) : SessionRuntimeCleanupHook {
    companion object {
        private const val SERVICE_TIME_CHANNEL_ID = "service_time_end_channel_v2"
        private const val WORK_TAG = "service_time_end_work_v2"
        private const val DEDUPLICATE_WINDOW_MILLIS = 30 * 60 * 1000L
        private const val RETRY_DELAY_SECONDS = 30L

        const val ACTION_SERVICE_TIME_END_ALARM = "com.ytone.longcare.SERVICE_TIME_END_ALARM"
        const val MAX_RETRY_COUNT = 3
    }

    private val lifecycleMutex = Mutex()

    private val scheduleDelegate by lazy {
        ServiceTimeNotificationScheduleDelegate(
            context = context,
            alarmManager = alarmManager,
            workManager = workManager,
            workTag = WORK_TAG,
            retryDelaySeconds = RETRY_DELAY_SECONDS,
            taskCodec = taskCodec,
        )
    }

    private val notificationUiDelegate by lazy {
        ServiceTimeNotificationUiDelegate(
            context = context,
            notificationManager = notificationManager,
            channelId = SERVICE_TIME_CHANNEL_ID,
            taskCodec = taskCodec,
        )
    }

    init {
        notificationUiDelegate.createNotificationChannel()
    }

    suspend fun scheduleServiceTimeEndNotification(
        orderId: Long,
        serviceName: String,
        serviceEndTimeMillis: Long,
    ) = lifecycleMutex.withLock {
        val lease = storageRegistry.requireCurrentLease()
        val payload = ServiceTimeTaskPayload(
            execution = taskCodec.currentExecution(lease, orderId),
            serviceName = serviceName,
            triggerAtMillis = serviceEndTimeMillis,
        )
        scheduleLocked(payload, persistReminder = true)
    }

    suspend fun reschedule(reminder: PendingServiceReminder) = lifecycleMutex.withLock {
        val lease = storageRegistry.requireCurrentLease()
        require(
            reminder.taskIdentity.namespaceId == lease.scopeKey.namespaceId() &&
                reminder.taskIdentity.sessionEpoch == lease.sessionEpoch
        ) { "Reminder does not belong to the current session" }
        scheduleLocked(
            payload = ServiceTimeTaskPayload(
                execution = ServiceTimeTaskExecution(reminder.taskIdentity, lease.generation),
                serviceName = reminder.serviceName,
                triggerAtMillis = reminder.triggerAtMillis,
            ),
            persistReminder = false,
        )
    }

    suspend fun cancelServiceTimeEndNotification(orderId: Long) = lifecycleMutex.withLock {
        val lease = storageRegistry.requireCurrentLease()
        val identity = taskCodec.currentExecution(lease, orderId).taskIdentity
        reminderRepository.delete(identity)
        scheduleDelegate.cancelAlarmManagerNotification(identity)
        scheduleDelegate.cancelWorkManagerNotification(identity)
        reminderRepository.clearProcessed(identity)
        notificationUiDelegate.cancel(identity)
        logI("服务时间结束通知已取消: orderId=$orderId")
    }

    suspend fun showServiceTimeEndNotification(orderId: Long, serviceName: String): Boolean {
        val lease = storageRegistry.requireCurrentLease()
        return handleTriggered(
            ServiceTimeTaskPayload(
                execution = taskCodec.currentExecution(lease, orderId),
                serviceName = serviceName,
                triggerAtMillis = 0,
            )
        )
    }

    suspend fun handleTriggered(payload: ServiceTimeTaskPayload): Boolean = lifecycleMutex.withLock {
        val lease = runCatching { storageRegistry.requireCurrentLease() }.getOrNull() ?: return@withLock false
        if (!taskCodec.matchesCurrent(payload.execution, lease)) {
            logI("忽略过期用户会话的服务通知: task=${payload.execution.taskIdentity.encode()}")
            return@withLock false
        }
        handleTriggeredWithoutLock(payload)
    }

    override suspend fun cleanup(identity: SessionRuntimeIdentity) = lifecycleMutex.withLock {
        val snapshot = reminderRepository.snapshotForCleanup(identity)
        val allTaskIdentities = buildSet {
            snapshot.pendingReminders.mapTo(this) { it.taskIdentity }
            snapshot.processedOrderIds.mapTo(this) { orderId -> identity.taskIdentity(orderId) }
        }
        snapshot.pendingReminders.forEach { reminder ->
            scheduleDelegate.cancelAlarmManagerNotification(reminder.taskIdentity)
            scheduleDelegate.cancelWorkManagerNotification(reminder.taskIdentity)
        }
        scheduleDelegate.cancelEpochAndAwait(identity.scopeKey.namespaceId(), identity.sessionEpoch)
        allTaskIdentities.forEach(notificationUiDelegate::cancel)
        reminderRepository.clearForCleanup(identity)
        logI("已清理撤销会话的服务提醒: count=${allTaskIdentities.size}")
    }

    private suspend fun scheduleLocked(
        payload: ServiceTimeTaskPayload,
        persistReminder: Boolean,
    ) {
        val identity = payload.execution.taskIdentity
        if (isProcessed(identity)) {
            logI("订单已处理，跳过重复通知: orderId=${payload.orderId}")
            return
        }
        val delayMillis = payload.triggerAtMillis - System.currentTimeMillis()
        if (delayMillis <= 0) {
            handleTriggeredWithoutLock(payload)
            return
        }
        if (persistReminder) {
            reminderRepository.upsert(
                PendingServiceReminder(
                    taskIdentity = identity,
                    orderId = payload.orderId,
                    serviceName = payload.serviceName,
                    triggerAtMillis = payload.triggerAtMillis,
                )
            )
        }
        scheduleDelegate.scheduleAlarmManagerNotification(payload)
        scheduleDelegate.scheduleWorkManagerNotification(payload, delayMillis)
        logI("持久化双通道通知调度完成: orderId=${payload.orderId}")
    }

    private suspend fun handleTriggeredWithoutLock(payload: ServiceTimeTaskPayload): Boolean {
        val lease = storageRegistry.requireCurrentLease()
        if (!taskCodec.matchesCurrent(payload.execution, lease)) return false
        val identity = payload.execution.taskIdentity
        if (isProcessed(identity)) {
            reminderRepository.delete(identity)
            return false
        }
        return try {
            notificationUiDelegate.showServiceTimeEndNotification(identity, payload.serviceName)
            reminderRepository.markProcessed(identity, System.currentTimeMillis())
            reminderRepository.delete(identity)
            logI("服务时间结束通知已显示: orderId=${payload.orderId}")
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logE("显示服务时间结束通知失败: ${error.message}", throwable = error)
            throw error
        }
    }

    private suspend fun isProcessed(identity: UserTaskIdentity): Boolean =
        reminderRepository.wasProcessedSince(
            identity,
            sinceMillis = System.currentTimeMillis() - DEDUPLICATE_WINDOW_MILLIS,
        )

    private fun SessionRuntimeIdentity.taskIdentity(orderId: Long) = UserTaskIdentity(
        namespaceId = scopeKey.namespaceId(),
        sessionEpoch = sessionEpoch,
        taskType = SERVICE_TIME_END_TASK_TYPE,
        businessId = orderId.toString(),
    )
}
