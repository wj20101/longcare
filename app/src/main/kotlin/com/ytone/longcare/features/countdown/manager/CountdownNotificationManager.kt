package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserCountdownTaskRepository
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserStorageLeaseAccess
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 倒计时通知管理器
 * 负责管理倒计时完成时的通知和AlarmManager
 */
@Singleton
class CountdownNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager,
    private val storageRegistry: UserStorageLeaseAccess,
    private val taskRepository: UserCountdownTaskRepository,
    private val taskCodec: CountdownTaskCodec,
    private val executionGate: CountdownTaskExecutionGate,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {

    companion object {
        private const val COUNTDOWN_NOTIFICATION_CHANNEL_ID = "countdown_completion_channel_v2"

        const val EXTRA_ORDER_KEY = "extra_order_key"
        const val EXTRA_SERVICE_NAME = "extra_service_name"

        fun extractOrderKey(
            intent: Intent?,
            defaultValue: OrderKey = OrderKey(orderId = -1L, planId = 0)
        ): OrderKey {
            if (intent == null) {
                return defaultValue
            }
            return extractSerializableOrderKey(intent, EXTRA_ORDER_KEY) ?: defaultValue
        }

        fun extractServiceName(intent: Intent?, defaultValue: String): String {
            if (intent == null) {
                return defaultValue
            }
            return intent.getStringExtra(EXTRA_SERVICE_NAME) ?: defaultValue
        }

        private fun extractSerializableOrderKey(intent: Intent, key: String): OrderKey? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(key, OrderKey::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(key) as? OrderKey
            }
        }
    }

    private val alarmDelegate by lazy {
        CountdownAlarmDelegate(
            context = context,
            alarmManager = alarmManager,
            codec = taskCodec,
        )
    }

    private val notificationUiDelegate by lazy {
        CountdownNotificationUiDelegate(
            context = context,
            notificationManager = notificationManager,
            channelId = COUNTDOWN_NOTIFICATION_CHANNEL_ID,
            codec = taskCodec,
        )
    }

    private val lifecycleMutex = Mutex()
    private val operationLock = Any()
    private val jobsBySession = mutableMapOf<String, MutableSet<Job>>()

    init {
        notificationUiDelegate.createNotificationChannel()
    }

    fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        triggerTimeMillis: Long,
    ) {
        val lease = currentLeaseOrNull() ?: return
        val payload = taskCodec.currentPayload(lease, orderKey, serviceName, triggerTimeMillis)
        launchForLease(lease) { scheduleNow(payload) }
    }

    fun cancelCountdownAlarm() {
        val lease = currentLeaseOrNull() ?: return
        launchForLease(lease) { cancelAllNow(lease) }
    }

    fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
        val lease = currentLeaseOrNull() ?: return
        launchForLease(lease) { cancelOrderNow(lease, orderKey) }
    }

    fun buildCountdownCompletionNotification(
        payload: CountdownTaskPayload,
    ): android.app.Notification? {
        if (!executionGate.isCurrent(payload)) return null
        return notificationUiDelegate.buildCountdownCompletionNotification(payload)
    }

    fun dismiss(payload: CountdownTaskPayload) {
        if (!executionGate.isCurrent(payload)) return
        val lease = currentLeaseOrNull() ?: return
        launchForLease(lease) { dismissNow(payload) }
    }

    fun cancelCompletionNotificationIfCurrent(payload: CountdownTaskPayload) {
        if (!executionGate.isCurrent(payload)) return
        notificationUiDelegate.cancelCountdownCompletionNotification(
            payload.execution.taskIdentity,
        )
    }

    fun completionNotificationId(payload: CountdownTaskPayload): Int =
        taskCodec.completionNotificationId(payload.execution.taskIdentity)

    fun foregroundNotificationId(payload: CountdownTaskPayload): Int =
        taskCodec.foregroundNotificationId(payload.execution.taskIdentity)

    fun captureCurrentPayload(
        orderKey: OrderKey,
        serviceName: String,
        triggerAtMillis: Long,
    ): CountdownTaskPayload? = currentLeaseOrNull()?.let { lease ->
        taskCodec.currentPayload(lease, orderKey, serviceName, triggerAtMillis)
    }

    suspend fun cleanup(identity: SessionRuntimeIdentity) {
        cancelAndJoinOperations(identity)
        lifecycleMutex.withLock {
            val tasks = taskRepository.snapshotForCleanup(identity)
            tasks.forEach { task -> cancelSystemTask(task.toTaskPayload()) }
            taskRepository.clearForCleanup(identity)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return alarmDelegate.canScheduleExactAlarms()
    }

    fun canUseFullScreenIntent(): Boolean {
        return notificationUiDelegate.canUseFullScreenIntent()
    }

    fun getFullScreenIntentPermissionStatus(): FullScreenIntentStatus {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                FullScreenIntentStatus.GRANTED_BY_DEFAULT
            }
            canUseFullScreenIntent() -> {
                FullScreenIntentStatus.GRANTED
            }
            else -> {
                FullScreenIntentStatus.DENIED
            }
        }
    }

    enum class FullScreenIntentStatus {
        GRANTED_BY_DEFAULT,
        GRANTED,
        DENIED
    }

    internal suspend fun scheduleNow(payload: CountdownTaskPayload): Boolean =
        lifecycleMutex.withLock {
            if (!executionGate.isCurrent(payload)) return@withLock false
            val existing = taskRepository.getAllForCurrentSession()
            existing.forEach { task ->
                cancelSystemTask(task.toTaskPayload())
                taskRepository.delete(task.taskIdentity)
            }
            if (!executionGate.isCurrent(payload)) return@withLock false
            taskRepository.upsert(payload.toPendingTask())
            if (!executionGate.isCurrent(payload)) return@withLock false
            val scheduled = alarmDelegate.scheduleCountdownAlarm(payload)
            if (!scheduled && executionGate.isCurrent(payload)) {
                taskRepository.delete(payload.execution.taskIdentity)
            }
            scheduled
        }

    internal suspend fun dismissNow(payload: CountdownTaskPayload): Boolean =
        lifecycleMutex.withLock {
            if (!executionGate.isCurrent(payload)) return@withLock false
            notificationUiDelegate.cancelCountdownCompletionNotification(
                payload.execution.taskIdentity,
            )
            taskRepository.delete(payload.execution.taskIdentity)
            true
        }

    private suspend fun cancelAllNow(lease: UserStorageLease) = lifecycleMutex.withLock {
        storageRegistry.requireValid(lease)
        taskRepository.getAllForCurrentSession().forEach { task ->
            cancelSystemTask(task.toTaskPayload())
            taskRepository.delete(task.taskIdentity)
        }
    }

    private suspend fun cancelOrderNow(lease: UserStorageLease, orderKey: OrderKey) =
        lifecycleMutex.withLock {
            storageRegistry.requireValid(lease)
            taskRepository.getAllForCurrentSession()
                .filter { it.orderKey == orderKey }
                .forEach { task ->
                    cancelSystemTask(task.toTaskPayload())
                    taskRepository.delete(task.taskIdentity)
                }
        }

    private fun cancelSystemTask(payload: CountdownTaskPayload) {
        alarmDelegate.cancelCountdownAlarm(payload.execution.taskIdentity)
        notificationUiDelegate.cancelCountdownCompletionNotification(
            payload.execution.taskIdentity,
        )
        notificationUiDelegate.cancelForegroundNotification(payload.execution.taskIdentity)
    }

    private fun currentLeaseOrNull(): UserStorageLease? =
        runCatching { storageRegistry.requireCurrentLease() }.getOrNull()

    private fun launchForLease(lease: UserStorageLease, operation: suspend () -> Unit) {
        val key = lease.sessionFingerprint()
        lateinit var job: Job
        job = applicationScope.launch(start = CoroutineStart.LAZY) {
            try {
                storageRegistry.requireValid(lease)
                operation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logE("倒计时用户任务执行失败: ${error.message}", throwable = error)
            } finally {
                synchronized(operationLock) {
                    jobsBySession[key]?.let { jobs ->
                        jobs.remove(job)
                        if (jobs.isEmpty()) jobsBySession.remove(key)
                    }
                }
            }
        }
        synchronized(operationLock) {
            jobsBySession.getOrPut(key, ::linkedSetOf).add(job)
        }
        job.start()
    }

    private suspend fun cancelAndJoinOperations(identity: SessionRuntimeIdentity) {
        val key = identity.sessionFingerprint()
        val jobs = synchronized(operationLock) {
            jobsBySession.remove(key).orEmpty().toList()
        }
        jobs.forEach { it.cancel(CancellationException("Countdown session was revoked")) }
        jobs.joinAll()
    }

    private fun UserStorageLease.sessionFingerprint(): String =
        "${scopeKey.namespaceId().value}:${sessionEpoch.value}"

    private fun SessionRuntimeIdentity.sessionFingerprint(): String =
        "${scopeKey.namespaceId().value}:${sessionEpoch.value}"
}

@Singleton
class CountdownTaskExecutionGate @Inject constructor(
    private val storageRegistry: UserStorageLeaseAccess,
    private val codec: CountdownTaskCodec,
) {
    fun isCurrent(payload: CountdownTaskPayload): Boolean {
        val lease = storageRegistry.currentLeaseOrNull() ?: return false
        return codec.matchesCurrent(payload, lease)
    }
}
