package com.ytone.longcare.domain.userstorage

const val SERVICE_TIME_END_TASK_TYPE = "service-time-end"

data class PendingServiceReminder(
    val taskIdentity: UserTaskIdentity,
    val orderId: Long,
    val serviceName: String,
    val triggerAtMillis: Long,
)

data class ServiceReminderCleanupSnapshot(
    val pendingReminders: List<PendingServiceReminder>,
    val processedOrderIds: Set<Long>,
)

interface UserServiceReminderRepository {
    suspend fun upsert(reminder: PendingServiceReminder)
    suspend fun getAllForCurrentSession(): List<PendingServiceReminder>
    suspend fun delete(taskIdentity: UserTaskIdentity)
    suspend fun deleteExpiredForCurrentSession(nowMillis: Long)

    suspend fun wasProcessedSince(taskIdentity: UserTaskIdentity, sinceMillis: Long): Boolean
    suspend fun markProcessed(taskIdentity: UserTaskIdentity, processedAtMillis: Long)
    suspend fun clearProcessed(taskIdentity: UserTaskIdentity)

    suspend fun snapshotForCleanup(identity: SessionRuntimeIdentity): ServiceReminderCleanupSnapshot
    suspend fun clearForCleanup(identity: SessionRuntimeIdentity)
}
