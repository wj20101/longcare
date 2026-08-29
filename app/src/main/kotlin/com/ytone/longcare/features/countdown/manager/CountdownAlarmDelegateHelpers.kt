package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.common.utils.klogE
import com.ytone.longcare.domain.userstorage.COUNTDOWN_TASK_TYPE
import com.ytone.longcare.domain.userstorage.PendingCountdownTask
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import com.ytone.longcare.domain.userstorage.countdownBusinessId
import com.ytone.longcare.model.NamespaceId
import com.ytone.longcare.model.OrderKey
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal fun logNextAlarmClock(alarmManager: AlarmManager) {
    try {
        alarmManager.nextAlarmClock?.let { next ->
            klogI("当前系统下一个闹钟时间: ${next.triggerTime}")
        }
    } catch (exception: Exception) {
        klogE("读取系统下一个闹钟失败", throwable = exception)
    }
}

internal fun cancelCountdownAlarmForIdentity(
    context: Context,
    alarmManager: AlarmManager,
    codec: CountdownTaskCodec,
    identity: UserTaskIdentity,
) {
    val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
        data = codec.dataUri(identity, CountdownIntentPurpose.ALARM)
    }
    val pendingIntent = PendingIntentCompat.getBroadcast(
        context,
        codec.requestCode(identity, CountdownIntentPurpose.ALARM),
        intent,
        PendingIntent.FLAG_NO_CREATE,
        false
    )
    if (pendingIntent == null) {
        klogI("用户任务 ${identity.encode()} 的倒计时闹钟不存在，跳过取消")
        return
    }
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
    klogI("✅ 用户任务 ${identity.encode()} 的倒计时闹钟已取消")
}

enum class CountdownIntentPurpose(val pathSegment: String) {
    ALARM("alarm"),
    ALARM_ACTIVITY("alarm-activity"),
    DISMISS("dismiss"),
    RINGTONE_SERVICE("ringtone-service"),
    COUNTDOWN_SERVICE("countdown-service"),
    COUNTDOWN_CONTENT("countdown-content"),
}

data class CountdownTaskExecution(
    val taskIdentity: UserTaskIdentity,
    val generation: StorageGeneration,
)

data class CountdownTaskPayload(
    val execution: CountdownTaskExecution,
    val orderKey: OrderKey,
    val serviceName: String,
    val triggerAtMillis: Long,
) {
    fun toPendingTask() = PendingCountdownTask(
        taskIdentity = execution.taskIdentity,
        generation = execution.generation,
        orderKey = orderKey,
        serviceName = serviceName,
        triggerAtMillis = triggerAtMillis,
    )
}

fun PendingCountdownTask.toTaskPayload() = CountdownTaskPayload(
    execution = CountdownTaskExecution(taskIdentity, generation),
    orderKey = orderKey,
    serviceName = serviceName,
    triggerAtMillis = triggerAtMillis,
)

@Singleton
class CountdownTaskCodec @Inject constructor() {
    fun currentPayload(
        lease: UserStorageLease,
        orderKey: OrderKey,
        serviceName: String,
        triggerAtMillis: Long,
    ) = CountdownTaskPayload(
        execution = CountdownTaskExecution(
            taskIdentity = UserTaskIdentity(
                namespaceId = lease.scopeKey.namespaceId(),
                sessionEpoch = lease.sessionEpoch,
                taskType = COUNTDOWN_TASK_TYPE,
                businessId = countdownBusinessId(orderKey),
            ),
            generation = lease.generation,
        ),
        orderKey = orderKey,
        serviceName = serviceName,
        triggerAtMillis = triggerAtMillis,
    )

    fun dataUri(identity: UserTaskIdentity, purpose: CountdownIntentPurpose): Uri = Uri.Builder()
        .scheme(TASK_URI_SCHEME)
        .authority(TASK_URI_AUTHORITY)
        .appendPath(TASK_URI_VERSION)
        .appendPath(identity.namespaceId.value)
        .appendPath(identity.sessionEpoch.value.toString())
        .appendPath(identity.taskType)
        .appendPath(identity.businessId)
        .appendPath(purpose.pathSegment)
        .build()

    fun requestCode(identity: UserTaskIdentity, purpose: CountdownIntentPurpose): Int =
        stablePositiveId("request:${purpose.pathSegment}", identity, REQUEST_ID_SEED)

    fun completionNotificationId(identity: UserTaskIdentity): Int =
        stablePositiveId("completion-notification", identity, COMPLETION_NOTIFICATION_ID_SEED)

    fun foregroundNotificationId(identity: UserTaskIdentity): Int =
        stablePositiveId("foreground-notification", identity, FOREGROUND_NOTIFICATION_ID_SEED)

    fun writeToIntent(
        intent: Intent,
        payload: CountdownTaskPayload,
        purpose: CountdownIntentPurpose,
    ): Intent = intent.apply {
        data = dataUri(payload.execution.taskIdentity, purpose)
        putExtra(KEY_NAMESPACE_ID, payload.execution.taskIdentity.namespaceId.value)
        putExtra(KEY_SESSION_EPOCH, payload.execution.taskIdentity.sessionEpoch.value)
        putExtra(KEY_STORAGE_GENERATION, payload.execution.generation.value)
        putExtra(KEY_TASK_TYPE, payload.execution.taskIdentity.taskType)
        putExtra(KEY_BUSINESS_ID, payload.execution.taskIdentity.businessId)
        putExtra(KEY_ORDER_ID, payload.orderKey.orderId)
        putExtra(KEY_PLAN_ID, payload.orderKey.planId)
        putExtra(KEY_SERVICE_NAME, payload.serviceName)
        putExtra(KEY_TRIGGER_AT_MILLIS, payload.triggerAtMillis)
    }

    fun fromIntent(intent: Intent?, purpose: CountdownIntentPurpose): CountdownTaskPayload? {
        if (intent == null) return null
        val namespaceId = intent.getStringExtra(KEY_NAMESPACE_ID)
        val sessionEpoch = intent.getLongExtra(KEY_SESSION_EPOCH, INVALID_LONG)
        val generation = intent.getLongExtra(KEY_STORAGE_GENERATION, INVALID_LONG)
        val taskType = intent.getStringExtra(KEY_TASK_TYPE)
        val businessId = intent.getStringExtra(KEY_BUSINESS_ID)
        val orderId = intent.getLongExtra(KEY_ORDER_ID, INVALID_LONG)
        val planId = intent.getIntExtra(KEY_PLAN_ID, INVALID_INT)
        val serviceName = intent.getStringExtra(KEY_SERVICE_NAME)
        val triggerAtMillis = intent.getLongExtra(KEY_TRIGGER_AT_MILLIS, INVALID_LONG)
        if (
            namespaceId.isNullOrBlank() ||
            sessionEpoch <= 0 ||
            generation <= 0 ||
            taskType != COUNTDOWN_TASK_TYPE ||
            businessId.isNullOrBlank() ||
            orderId < 0 ||
            planId == INVALID_INT ||
            serviceName == null ||
            triggerAtMillis < 0
        ) {
            return null
        }
        val orderKey = OrderKey(orderId = orderId, planId = planId)
        if (businessId != countdownBusinessId(orderKey)) return null
        val payload = runCatching {
            CountdownTaskPayload(
                execution = CountdownTaskExecution(
                    taskIdentity = UserTaskIdentity(
                        namespaceId = NamespaceId(namespaceId),
                        sessionEpoch = com.ytone.longcare.domain.userstorage.SessionEpoch(sessionEpoch),
                        taskType = taskType,
                        businessId = businessId,
                    ),
                    generation = StorageGeneration(generation),
                ),
                orderKey = orderKey,
                serviceName = serviceName,
                triggerAtMillis = triggerAtMillis,
            )
        }.getOrNull() ?: return null
        return payload.takeIf {
            intent.data == dataUri(it.execution.taskIdentity, purpose)
        }
    }

    fun matchesCurrent(payload: CountdownTaskPayload, lease: UserStorageLease): Boolean =
        payload.execution.taskIdentity.namespaceId == lease.scopeKey.namespaceId() &&
            payload.execution.taskIdentity.sessionEpoch == lease.sessionEpoch &&
            payload.execution.taskIdentity.taskType == COUNTDOWN_TASK_TYPE &&
            payload.execution.taskIdentity.businessId == countdownBusinessId(payload.orderKey) &&
            payload.execution.generation == lease.generation

    private fun stablePositiveId(
        prefix: String,
        identity: UserTaskIdentity,
        seed: Int,
    ): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "$prefix|${identity.encode()}".toByteArray(StandardCharsets.UTF_8),
        )
        val positiveHash = ByteBuffer.wrap(digest, 0, Int.SIZE_BYTES).int and Int.MAX_VALUE
        return seed + (positiveHash % (Int.MAX_VALUE - seed))
    }

    private companion object {
        const val TASK_URI_SCHEME = "longcare"
        const val TASK_URI_AUTHORITY = "user-task"
        const val TASK_URI_VERSION = "v1"
        const val REQUEST_ID_SEED = 20_001
        const val COMPLETION_NOTIFICATION_ID_SEED = 30_001
        const val FOREGROUND_NOTIFICATION_ID_SEED = 40_001
        const val INVALID_LONG = -1L
        const val INVALID_INT = Int.MIN_VALUE

        const val KEY_NAMESPACE_ID = "countdown_task_namespace_id"
        const val KEY_SESSION_EPOCH = "countdown_task_session_epoch"
        const val KEY_STORAGE_GENERATION = "countdown_task_storage_generation"
        const val KEY_TASK_TYPE = "countdown_task_type"
        const val KEY_BUSINESS_ID = "countdown_task_business_id"
        const val KEY_ORDER_ID = "countdown_task_order_id"
        const val KEY_PLAN_ID = "countdown_task_plan_id"
        const val KEY_SERVICE_NAME = "countdown_task_service_name"
        const val KEY_TRIGGER_AT_MILLIS = "countdown_task_trigger_at_millis"
    }
}
