package com.ytone.longcare.domain.userstorage

import com.ytone.longcare.model.OrderKey

const val COUNTDOWN_TASK_TYPE = "service-countdown"

data class PendingCountdownTask(
    val taskIdentity: UserTaskIdentity,
    val generation: StorageGeneration,
    val orderKey: OrderKey,
    val serviceName: String,
    val triggerAtMillis: Long,
)

interface UserCountdownTaskRepository {
    suspend fun upsert(task: PendingCountdownTask)
    suspend fun getAllForCurrentSession(): List<PendingCountdownTask>
    suspend fun delete(taskIdentity: UserTaskIdentity)
    suspend fun snapshotForCleanup(identity: SessionRuntimeIdentity): List<PendingCountdownTask>
    suspend fun clearForCleanup(identity: SessionRuntimeIdentity)
}

fun countdownBusinessId(orderKey: OrderKey): String =
    "${orderKey.orderId}:${orderKey.planId}"

fun countdownOrderKey(businessId: String): OrderKey? {
    val separator = businessId.indexOf(':')
    if (separator <= 0 || separator == businessId.lastIndex) return null
    val orderId = businessId.substring(0, separator).toLongOrNull() ?: return null
    val planId = businessId.substring(separator + 1).toIntOrNull() ?: return null
    return OrderKey(orderId = orderId, planId = planId)
}
