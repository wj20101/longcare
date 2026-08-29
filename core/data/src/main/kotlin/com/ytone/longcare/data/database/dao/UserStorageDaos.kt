package com.ytone.longcare.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ytone.longcare.data.database.entity.InitialOrderSnapshotEntityDb
import com.ytone.longcare.data.database.entity.PendingServiceReminderEntityDb
import com.ytone.longcare.data.database.entity.ProcessedServiceNotificationEntityDb
import com.ytone.longcare.data.database.entity.UserNamespaceMetadataEntityDb
import kotlinx.coroutines.flow.Flow

@Dao
interface UserNamespaceMetadataDao {
    @Query("SELECT * FROM user_namespace_metadata WHERE id = 1 LIMIT 1")
    suspend fun get(): UserNamespaceMetadataEntityDb?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(metadata: UserNamespaceMetadataEntityDb)
}

@Dao
interface PendingServiceReminderDao {
    @Query("SELECT * FROM pending_service_reminders WHERE session_epoch = :sessionEpoch AND task_type = :taskType ORDER BY trigger_at_millis")
    fun observeAll(sessionEpoch: Long, taskType: String): Flow<List<PendingServiceReminderEntityDb>>

    @Query("SELECT * FROM pending_service_reminders WHERE session_epoch = :sessionEpoch AND task_type = :taskType ORDER BY trigger_at_millis")
    suspend fun getAll(sessionEpoch: Long, taskType: String): List<PendingServiceReminderEntityDb>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: PendingServiceReminderEntityDb)

    @Query("DELETE FROM pending_service_reminders WHERE task_identity = :taskIdentity")
    suspend fun delete(taskIdentity: String)

    @Query("DELETE FROM pending_service_reminders WHERE session_epoch = :sessionEpoch AND task_type = :taskType AND trigger_at_millis <= :nowMillis")
    suspend fun deleteExpired(sessionEpoch: Long, taskType: String, nowMillis: Long)

    @Query("DELETE FROM pending_service_reminders WHERE session_epoch = :sessionEpoch AND task_type = :taskType")
    suspend fun deleteAll(sessionEpoch: Long, taskType: String)
}

@Dao
interface ProcessedServiceNotificationDao {
    @Query("SELECT EXISTS(SELECT 1 FROM processed_service_notifications WHERE task_identity = :taskIdentity AND processed_at_millis >= :sinceMillis)")
    suspend fun existsSince(taskIdentity: String, sinceMillis: Long): Boolean

    @Query("SELECT order_id FROM processed_service_notifications WHERE session_epoch = :sessionEpoch")
    suspend fun getOrderIds(sessionEpoch: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ProcessedServiceNotificationEntityDb)

    @Query("DELETE FROM processed_service_notifications WHERE task_identity = :taskIdentity")
    suspend fun delete(taskIdentity: String)

    @Query("DELETE FROM processed_service_notifications WHERE session_epoch = :sessionEpoch")
    suspend fun deleteAll(sessionEpoch: Long)
}

@Dao
interface InitialOrderSnapshotDao {
    @Query("SELECT * FROM initial_order_snapshots ORDER BY list_type, order_id, plan_id")
    suspend fun getAll(): List<InitialOrderSnapshotEntityDb>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<InitialOrderSnapshotEntityDb>)

    @Query("DELETE FROM initial_order_snapshots")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(snapshots: List<InitialOrderSnapshotEntityDb>) {
        deleteAll()
        if (snapshots.isNotEmpty()) insertAll(snapshots)
    }
}
