package com.ytone.longcare.data.userstorage

import com.ytone.longcare.data.database.entity.PendingServiceReminderEntityDb
import com.ytone.longcare.domain.userstorage.COUNTDOWN_TASK_TYPE
import com.ytone.longcare.domain.userstorage.PendingCountdownTask
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserCountdownTaskRepository
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserStorageState
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import com.ytone.longcare.domain.userstorage.countdownBusinessId
import com.ytone.longcare.domain.userstorage.countdownOrderKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomUserCountdownTaskRepository @Inject internal constructor(
    private val databaseAccess: UserDatabaseAccess,
    private val storageRegistry: UserStorageRegistry,
) : UserCountdownTaskRepository {

    override suspend fun upsert(task: PendingCountdownTask) {
        val lease = databaseAccess.currentLease()
        requireTaskMatches(lease, task)
        databaseAccess.withLease(lease) { database, _ ->
            database.pendingServiceReminderDao().upsert(task.toDb())
        }
    }

    override suspend fun getAllForCurrentSession(): List<PendingCountdownTask> {
        val lease = databaseAccess.currentLease()
        return databaseAccess.withLease(lease) { database, _ ->
            database.pendingServiceReminderDao()
                .getAll(lease.sessionEpoch.value, COUNTDOWN_TASK_TYPE)
                .mapNotNull { row -> row.toDomainOrNull(lease) }
        }
    }

    override suspend fun delete(taskIdentity: UserTaskIdentity) {
        val lease = databaseAccess.currentLease()
        requireIdentityMatches(lease, taskIdentity)
        databaseAccess.withLease(lease) { database, _ ->
            database.pendingServiceReminderDao().delete(taskIdentity.encode())
        }
    }

    override suspend fun snapshotForCleanup(
        identity: SessionRuntimeIdentity,
    ): List<PendingCountdownTask> = storageRegistry.withRevokedDatabase(identity) { database ->
        val lease = databaseLeaseForCleanup(identity)
        database.pendingServiceReminderDao()
            .getAll(identity.sessionEpoch.value, COUNTDOWN_TASK_TYPE)
            .mapNotNull { row -> row.toDomainOrNull(lease) }
    }.orEmpty()

    override suspend fun clearForCleanup(identity: SessionRuntimeIdentity) {
        storageRegistry.withRevokedDatabase(identity) { database ->
            database.pendingServiceReminderDao().deleteAll(
                identity.sessionEpoch.value,
                COUNTDOWN_TASK_TYPE,
            )
        }
    }

    private fun requireTaskMatches(lease: UserStorageLease, task: PendingCountdownTask) {
        requireIdentityMatches(lease, task.taskIdentity)
        require(task.generation == lease.generation) {
            "Countdown task generation does not match current storage"
        }
        require(task.taskIdentity.businessId == countdownBusinessId(task.orderKey)) {
            "Countdown task business ID does not match order"
        }
    }

    private fun requireIdentityMatches(lease: UserStorageLease, identity: UserTaskIdentity) {
        require(identity.namespaceId == lease.scopeKey.namespaceId()) {
            "Countdown task namespace does not match current user storage"
        }
        require(identity.sessionEpoch == lease.sessionEpoch) {
            "Countdown task epoch does not match current user session"
        }
        require(identity.taskType == COUNTDOWN_TASK_TYPE) {
            "Unsupported countdown task type"
        }
        require(countdownOrderKey(identity.businessId) != null) {
            "Invalid countdown task business ID"
        }
    }

    private fun PendingServiceReminderEntityDb.toDomainOrNull(
        lease: UserStorageLease,
    ): PendingCountdownTask? {
        val orderKey = countdownOrderKey(businessId) ?: return null
        val identity = UserTaskIdentity(
            namespaceId = lease.scopeKey.namespaceId(),
            sessionEpoch = lease.sessionEpoch,
            taskType = COUNTDOWN_TASK_TYPE,
            businessId = businessId,
        )
        if (
            sessionEpoch != lease.sessionEpoch.value ||
            storageGeneration <= 0 ||
            taskType != COUNTDOWN_TASK_TYPE ||
            orderId != orderKey.orderId ||
            taskIdentity != identity.encode()
        ) {
            return null
        }
        return PendingCountdownTask(
            taskIdentity = identity,
            generation = StorageGeneration(storageGeneration),
            orderKey = orderKey,
            serviceName = serviceName,
            triggerAtMillis = triggerAtMillis,
        )
    }

    private fun PendingCountdownTask.toDb() = PendingServiceReminderEntityDb(
        taskIdentity = taskIdentity.encode(),
        orderId = orderKey.orderId,
        serviceName = serviceName,
        triggerAtMillis = triggerAtMillis,
        sessionEpoch = taskIdentity.sessionEpoch.value,
        storageGeneration = generation.value,
        taskType = taskIdentity.taskType,
        businessId = taskIdentity.businessId,
    )

    private fun databaseLeaseForCleanup(identity: SessionRuntimeIdentity): UserStorageLease {
        val lease = (storageRegistry.state.value as? UserStorageState.Closing)?.lease
            ?: throw UserStorageUnavailableException("Cleanup storage is not revoked")
        check(lease.scopeKey == identity.scopeKey && lease.sessionEpoch == identity.sessionEpoch)
        return lease
    }
}
