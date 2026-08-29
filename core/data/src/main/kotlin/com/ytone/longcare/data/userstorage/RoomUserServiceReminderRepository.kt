package com.ytone.longcare.data.userstorage

import androidx.room.withTransaction
import com.ytone.longcare.data.database.entity.PendingServiceReminderEntityDb
import com.ytone.longcare.data.database.entity.ProcessedServiceNotificationEntityDb
import com.ytone.longcare.domain.userstorage.PendingServiceReminder
import com.ytone.longcare.domain.userstorage.SERVICE_TIME_END_TASK_TYPE
import com.ytone.longcare.domain.userstorage.ServiceReminderCleanupSnapshot
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserServiceReminderRepository
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomUserServiceReminderRepository @Inject internal constructor(
    private val databaseAccess: UserDatabaseAccess,
    private val storageRegistry: UserStorageRegistry,
) : UserServiceReminderRepository {
    override suspend fun upsert(reminder: PendingServiceReminder) {
        val lease = databaseAccess.currentLease()
        requireTaskMatches(lease, reminder.taskIdentity, reminder.orderId)
        databaseAccess.withLease(lease) { database, _ ->
            database.pendingServiceReminderDao().upsert(reminder.toDb())
        }
    }

    override suspend fun getAllForCurrentSession(): List<PendingServiceReminder> {
        val lease = databaseAccess.currentLease()
        return databaseAccess.withLease(lease) { database, _ ->
            database.pendingServiceReminderDao()
                .getAll(lease.sessionEpoch.value, SERVICE_TIME_END_TASK_TYPE)
                .mapNotNull { row -> row.toDomainOrNull(lease) }
        }
    }

    override suspend fun delete(taskIdentity: UserTaskIdentity) {
        val lease = databaseAccess.currentLease()
        requireTaskMatches(lease, taskIdentity)
        databaseAccess.withLease(lease) { database, _ ->
            database.pendingServiceReminderDao().delete(taskIdentity.encode())
        }
    }

    override suspend fun deleteExpiredForCurrentSession(nowMillis: Long) {
        val lease = databaseAccess.currentLease()
        databaseAccess.withLease(lease) { database, _ ->
            database.pendingServiceReminderDao().deleteExpired(
                lease.sessionEpoch.value,
                SERVICE_TIME_END_TASK_TYPE,
                nowMillis,
            )
        }
    }

    override suspend fun wasProcessedSince(
        taskIdentity: UserTaskIdentity,
        sinceMillis: Long,
    ): Boolean {
        val lease = databaseAccess.currentLease()
        requireTaskMatches(lease, taskIdentity)
        return databaseAccess.withLease(lease) { database, _ ->
            database.processedServiceNotificationDao().existsSince(taskIdentity.encode(), sinceMillis)
        }
    }

    override suspend fun markProcessed(
        taskIdentity: UserTaskIdentity,
        processedAtMillis: Long,
    ) {
        val lease = databaseAccess.currentLease()
        val orderId = taskIdentity.businessId.toLongOrNull()
            ?: throw IllegalArgumentException("Service reminder businessId must be an order ID")
        requireTaskMatches(lease, taskIdentity, orderId)
        databaseAccess.withLease(lease) { database, _ ->
            database.processedServiceNotificationDao().upsert(
                ProcessedServiceNotificationEntityDb(
                    taskIdentity = taskIdentity.encode(),
                    orderId = orderId,
                    sessionEpoch = taskIdentity.sessionEpoch.value,
                    processedAtMillis = processedAtMillis,
                )
            )
        }
    }

    override suspend fun clearProcessed(taskIdentity: UserTaskIdentity) {
        val lease = databaseAccess.currentLease()
        requireTaskMatches(lease, taskIdentity)
        databaseAccess.withLease(lease) { database, _ ->
            database.processedServiceNotificationDao().delete(taskIdentity.encode())
        }
    }

    override suspend fun snapshotForCleanup(
        identity: SessionRuntimeIdentity,
    ): ServiceReminderCleanupSnapshot = storageRegistry.withRevokedDatabase(identity) { database ->
        val lease = databaseLeaseForCleanup(identity)
        ServiceReminderCleanupSnapshot(
            pendingReminders = database.pendingServiceReminderDao()
                .getAll(identity.sessionEpoch.value, SERVICE_TIME_END_TASK_TYPE)
                .mapNotNull { row -> row.toDomainOrNull(lease) },
            processedOrderIds = database.processedServiceNotificationDao()
                .getOrderIds(identity.sessionEpoch.value)
                .toSet(),
        )
    } ?: ServiceReminderCleanupSnapshot(emptyList(), emptySet())

    override suspend fun clearForCleanup(identity: SessionRuntimeIdentity) {
        storageRegistry.withRevokedDatabase(identity) { database ->
            database.withTransaction {
                database.pendingServiceReminderDao().deleteAll(
                    identity.sessionEpoch.value,
                    SERVICE_TIME_END_TASK_TYPE,
                )
                database.processedServiceNotificationDao().deleteAll(identity.sessionEpoch.value)
            }
        }
    }

    private fun requireTaskMatches(
        lease: UserStorageLease,
        taskIdentity: UserTaskIdentity,
        orderId: Long? = null,
    ) {
        require(taskIdentity.namespaceId == lease.scopeKey.namespaceId()) {
            "Task namespace does not match current user storage"
        }
        require(taskIdentity.sessionEpoch == lease.sessionEpoch) {
            "Task epoch does not match current user session"
        }
        require(taskIdentity.taskType == SERVICE_TIME_END_TASK_TYPE) {
            "Unsupported reminder task type"
        }
        if (orderId != null) {
            require(taskIdentity.businessId == orderId.toString()) {
                "Task business ID does not match reminder order"
            }
        }
    }

    private fun PendingServiceReminderEntityDb.toDomainOrNull(
        lease: UserStorageLease,
    ): PendingServiceReminder? {
        val identity = UserTaskIdentity(
            namespaceId = lease.scopeKey.namespaceId(),
            sessionEpoch = lease.sessionEpoch,
            taskType = SERVICE_TIME_END_TASK_TYPE,
            businessId = orderId.toString(),
        )
        if (
            sessionEpoch != lease.sessionEpoch.value ||
            taskType != SERVICE_TIME_END_TASK_TYPE ||
            businessId != orderId.toString() ||
            taskIdentity != identity.encode()
        ) {
            return null
        }
        return PendingServiceReminder(
            taskIdentity = identity,
            orderId = orderId,
            serviceName = serviceName,
            triggerAtMillis = triggerAtMillis,
        )
    }

    private fun PendingServiceReminder.toDb() = PendingServiceReminderEntityDb(
        taskIdentity = taskIdentity.encode(),
        orderId = orderId,
        serviceName = serviceName,
        triggerAtMillis = triggerAtMillis,
        sessionEpoch = taskIdentity.sessionEpoch.value,
        storageGeneration = storageRegistry.requireCurrentLease().generation.value,
        taskType = taskIdentity.taskType,
        businessId = taskIdentity.businessId,
    )

    private fun databaseLeaseForCleanup(identity: SessionRuntimeIdentity): UserStorageLease {
        val state = storageRegistry.state.value
        val lease = (state as? com.ytone.longcare.domain.userstorage.UserStorageState.Closing)?.lease
            ?: throw UserStorageUnavailableException("Cleanup storage is not revoked")
        check(lease.scopeKey == identity.scopeKey && lease.sessionEpoch == identity.sessionEpoch)
        return lease
    }
}
