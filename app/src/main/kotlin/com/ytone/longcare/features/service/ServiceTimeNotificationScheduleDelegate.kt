package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.service.receiver.ServiceTimeAlarmReceiver
import com.ytone.longcare.domain.userstorage.SERVICE_TIME_END_TASK_TYPE
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import com.ytone.longcare.model.NamespaceId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

internal class ServiceTimeNotificationScheduleDelegate(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val workManager: WorkManager,
    private val workTag: String,
    private val retryDelaySeconds: Long,
    private val taskCodec: ServiceTimeTaskCodec,
) {
    fun scheduleAlarmManagerNotification(
        payload: ServiceTimeTaskPayload,
    ) {
        try {
            val intent = taskCodec.writeToAlarmIntent(
                Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
                },
                payload,
            )
            val pendingIntent = PendingIntentCompat.getBroadcast(
                context,
                taskCodec.alarmRequestCode(payload.execution.taskIdentity),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false
            ) ?: throw IllegalStateException("创建 AlarmManager PendingIntent 失败: orderId=${payload.orderId}")

            scheduleAlarm(
                triggerTimeMillis = payload.triggerAtMillis,
                pendingIntent = pendingIntent,
            )
            logI("AlarmManager通知已设置: orderId=${payload.orderId}, triggerTime=${payload.triggerAtMillis}")
        } catch (e: Exception) {
            logE("设置AlarmManager通知失败: ${e.message}")
            throw e
        }
    }

    fun scheduleWorkManagerNotification(
        payload: ServiceTimeTaskPayload,
        delayMillis: Long,
    ) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<ServiceTimeEndWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(taskCodec.toWorkData(payload))
                .addTag(workTag)
                .addTag(taskCodec.workTag(payload.execution.taskIdentity))
                .addTag(
                    taskCodec.epochWorkTag(
                        payload.execution.taskIdentity.namespaceId,
                        payload.execution.taskIdentity.sessionEpoch,
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    retryDelaySeconds,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                taskCodec.workUniqueName(payload.execution.taskIdentity),
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            logI("WorkManager通知已设置: orderId=${payload.orderId}, delay=$delayMillis")
        } catch (e: Exception) {
            logE("设置WorkManager通知失败: ${e.message}")
            throw e
        }
    }

    fun cancelAlarmManagerNotification(taskIdentity: com.ytone.longcare.domain.userstorage.UserTaskIdentity) {
        try {
            val intent = Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
                data = taskCodec.alarmDataUri(taskIdentity)
            }
            val pendingIntent = PendingIntentCompat.getBroadcast(
                context,
                taskCodec.alarmRequestCode(taskIdentity),
                intent,
                PendingIntent.FLAG_NO_CREATE,
                false
            )
            if (pendingIntent == null) {
                logI("AlarmManager通知不存在，跳过取消: task=${taskIdentity.encode()}")
                return
            }
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            logI("AlarmManager通知已取消: task=${taskIdentity.encode()}")
        } catch (e: Exception) {
            logE("取消AlarmManager通知失败: ${e.message}")
        }
    }

    fun cancelWorkManagerNotification(taskIdentity: com.ytone.longcare.domain.userstorage.UserTaskIdentity) {
        try {
            workManager.cancelUniqueWork(taskCodec.workUniqueName(taskIdentity))
            logI("WorkManager通知已取消: task=${taskIdentity.encode()}")
        } catch (e: Exception) {
            logE("取消WorkManager通知失败: ${e.message}")
        }
    }

    suspend fun cancelEpochAndAwait(
        namespaceId: com.ytone.longcare.model.NamespaceId,
        sessionEpoch: com.ytone.longcare.domain.userstorage.SessionEpoch,
    ) {
        workManager.cancelAllWorkByTag(taskCodec.epochWorkTag(namespaceId, sessionEpoch))
            .result
            .awaitCompletion()
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun scheduleAlarm(
        triggerTimeMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        if (canScheduleExactAlarms()) {
            try {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent,
                )
                return
            } catch (exception: SecurityException) {
                logE("精确闹钟权限在调度时不可用，降级为非精确闹钟: ${exception.message}")
            }
        } else {
            logI("无精确闹钟权限，降级为 setAndAllowWhileIdle")
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent,
        )
    }

}

private suspend fun ListenableFuture<*>.awaitCompletion() {
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                val result = runCatching { get() }.fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { error ->
                        Result.failure(
                            if (error is ExecutionException) error.cause ?: error else error
                        )
                    },
                )
                if (continuation.isActive) continuation.resumeWith(result)
            },
            DIRECT_EXECUTOR,
        )
    }
}

private val DIRECT_EXECUTOR = Executor(Runnable::run)

data class ServiceTimeTaskExecution(
    val taskIdentity: UserTaskIdentity,
    val generation: StorageGeneration,
)

data class ServiceTimeTaskPayload(
    val execution: ServiceTimeTaskExecution,
    val serviceName: String,
    val triggerAtMillis: Long,
) {
    val orderId: Long
        get() = checkNotNull(execution.taskIdentity.businessId.toLongOrNull())
}

@Singleton
class ServiceTimeTaskCodec @Inject constructor() {
    fun currentExecution(lease: UserStorageLease, orderId: Long) = ServiceTimeTaskExecution(
        taskIdentity = UserTaskIdentity(
            namespaceId = lease.scopeKey.namespaceId(),
            sessionEpoch = lease.sessionEpoch,
            taskType = SERVICE_TIME_END_TASK_TYPE,
            businessId = orderId.toString(),
        ),
        generation = lease.generation,
    )

    fun workUniqueName(identity: UserTaskIdentity): String =
        "longcare-work:${identity.encode()}"

    fun workTag(identity: UserTaskIdentity): String =
        "longcare-work-tag:${identity.encode()}"

    fun epochWorkTag(namespaceId: NamespaceId, sessionEpoch: SessionEpoch): String =
        "longcare-work-epoch:${namespaceId.value}:${sessionEpoch.value}"

    fun alarmDataUri(identity: UserTaskIdentity): Uri = Uri.Builder()
        .scheme(TASK_URI_SCHEME)
        .authority(TASK_URI_AUTHORITY)
        .appendPath(TASK_URI_VERSION)
        .appendPath(identity.namespaceId.value)
        .appendPath(identity.sessionEpoch.value.toString())
        .appendPath(identity.taskType)
        .appendPath(identity.businessId)
        .build()

    fun alarmRequestCode(identity: UserTaskIdentity): Int = stablePositiveId(
        prefix = "alarm",
        identity = identity,
        seed = ALARM_ID_SEED,
    )

    fun notificationId(identity: UserTaskIdentity): Int = stablePositiveId(
        prefix = "notification",
        identity = identity,
        seed = NOTIFICATION_ID_SEED,
    )

    fun deduplicationKey(identity: UserTaskIdentity): String =
        "service-notification:${identity.encode()}"

    fun toWorkData(payload: ServiceTimeTaskPayload): Data = workDataOf(
        KEY_NAMESPACE_ID to payload.execution.taskIdentity.namespaceId.value,
        KEY_SESSION_EPOCH to payload.execution.taskIdentity.sessionEpoch.value,
        KEY_STORAGE_GENERATION to payload.execution.generation.value,
        KEY_TASK_TYPE to payload.execution.taskIdentity.taskType,
        KEY_BUSINESS_ID to payload.execution.taskIdentity.businessId,
        KEY_SERVICE_NAME to payload.serviceName,
        KEY_TRIGGER_AT_MILLIS to payload.triggerAtMillis,
    )

    fun fromWorkData(data: Data): ServiceTimeTaskPayload? = decode(
        namespaceId = data.getString(KEY_NAMESPACE_ID),
        sessionEpoch = data.getLong(KEY_SESSION_EPOCH, INVALID_LONG),
        generation = data.getLong(KEY_STORAGE_GENERATION, INVALID_LONG),
        taskType = data.getString(KEY_TASK_TYPE),
        businessId = data.getString(KEY_BUSINESS_ID),
        serviceName = data.getString(KEY_SERVICE_NAME),
        triggerAtMillis = data.getLong(KEY_TRIGGER_AT_MILLIS, INVALID_LONG),
    )

    fun writeToAlarmIntent(intent: Intent, payload: ServiceTimeTaskPayload): Intent = intent.apply {
        data = alarmDataUri(payload.execution.taskIdentity)
        putExtra(KEY_NAMESPACE_ID, payload.execution.taskIdentity.namespaceId.value)
        putExtra(KEY_SESSION_EPOCH, payload.execution.taskIdentity.sessionEpoch.value)
        putExtra(KEY_STORAGE_GENERATION, payload.execution.generation.value)
        putExtra(KEY_TASK_TYPE, payload.execution.taskIdentity.taskType)
        putExtra(KEY_BUSINESS_ID, payload.execution.taskIdentity.businessId)
        putExtra(KEY_SERVICE_NAME, payload.serviceName)
        putExtra(KEY_TRIGGER_AT_MILLIS, payload.triggerAtMillis)
    }

    fun fromAlarmIntent(intent: Intent): ServiceTimeTaskPayload? {
        val payload = decode(
            namespaceId = intent.getStringExtra(KEY_NAMESPACE_ID),
            sessionEpoch = intent.getLongExtra(KEY_SESSION_EPOCH, INVALID_LONG),
            generation = intent.getLongExtra(KEY_STORAGE_GENERATION, INVALID_LONG),
            taskType = intent.getStringExtra(KEY_TASK_TYPE),
            businessId = intent.getStringExtra(KEY_BUSINESS_ID),
            serviceName = intent.getStringExtra(KEY_SERVICE_NAME),
            triggerAtMillis = intent.getLongExtra(KEY_TRIGGER_AT_MILLIS, INVALID_LONG),
        ) ?: return null
        return payload.takeIf { intent.data == alarmDataUri(it.execution.taskIdentity) }
    }

    fun matchesCurrent(execution: ServiceTimeTaskExecution, lease: UserStorageLease): Boolean =
        execution.taskIdentity.namespaceId == lease.scopeKey.namespaceId() &&
            execution.taskIdentity.sessionEpoch == lease.sessionEpoch &&
            execution.generation == lease.generation &&
            execution.taskIdentity.taskType == SERVICE_TIME_END_TASK_TYPE

    private fun decode(
        namespaceId: String?,
        sessionEpoch: Long,
        generation: Long,
        taskType: String?,
        businessId: String?,
        serviceName: String?,
        triggerAtMillis: Long,
    ): ServiceTimeTaskPayload? {
        if (
            namespaceId.isNullOrBlank() ||
            sessionEpoch <= 0 ||
            generation <= 0 ||
            taskType != SERVICE_TIME_END_TASK_TYPE ||
            businessId.isNullOrBlank() ||
            businessId.toLongOrNull() == null ||
            serviceName == null ||
            triggerAtMillis < 0
        ) {
            return null
        }
        return runCatching {
            ServiceTimeTaskPayload(
                execution = ServiceTimeTaskExecution(
                    taskIdentity = UserTaskIdentity(
                        namespaceId = NamespaceId(namespaceId),
                        sessionEpoch = SessionEpoch(sessionEpoch),
                        taskType = taskType,
                        businessId = businessId,
                    ),
                    generation = StorageGeneration(generation),
                ),
                serviceName = serviceName,
                triggerAtMillis = triggerAtMillis,
            )
        }.getOrNull()
    }

    private fun stablePositiveId(
        prefix: String,
        identity: UserTaskIdentity,
        seed: Int,
    ): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "$prefix|${identity.encode()}".toByteArray(StandardCharsets.UTF_8)
        )
        val positiveHash = ByteBuffer.wrap(digest, 0, Int.SIZE_BYTES).int and Int.MAX_VALUE
        val range = Int.MAX_VALUE - seed
        return seed + (positiveHash % range)
    }

    private companion object {
        const val TASK_URI_SCHEME = "longcare"
        const val TASK_URI_AUTHORITY = "user-task"
        const val TASK_URI_VERSION = "v1"
        const val ALARM_ID_SEED = 5_001
        const val NOTIFICATION_ID_SEED = 4_001
        const val INVALID_LONG = -1L

        const val KEY_NAMESPACE_ID = "user_task_namespace_id"
        const val KEY_SESSION_EPOCH = "user_task_session_epoch"
        const val KEY_STORAGE_GENERATION = "user_task_storage_generation"
        const val KEY_TASK_TYPE = "user_task_type"
        const val KEY_BUSINESS_ID = "user_task_business_id"
        const val KEY_SERVICE_NAME = "user_task_service_name"
        const val KEY_TRIGGER_AT_MILLIS = "user_task_trigger_at_millis"
    }
}
