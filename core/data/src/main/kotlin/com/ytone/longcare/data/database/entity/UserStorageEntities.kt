package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_namespace_metadata")
data class UserNamespaceMetadataEntityDb(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "format_version")
    val formatVersion: Int,
    @ColumnInfo(name = "namespace_id")
    val namespaceId: String,
    @ColumnInfo(name = "company_id")
    val companyId: Int,
    @ColumnInfo(name = "account_id")
    val accountId: Int,
    @ColumnInfo(name = "user_id")
    val userId: Int,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

@Entity(
    tableName = "pending_service_reminders",
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["session_epoch"]),
        Index(value = ["task_type", "session_epoch"]),
    ],
)
data class PendingServiceReminderEntityDb(
    @PrimaryKey
    @ColumnInfo(name = "task_identity")
    val taskIdentity: String,
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "service_name")
    val serviceName: String,
    @ColumnInfo(name = "trigger_at_millis")
    val triggerAtMillis: Long,
    @ColumnInfo(name = "session_epoch")
    val sessionEpoch: Long,
    @ColumnInfo(name = "storage_generation")
    val storageGeneration: Long,
    @ColumnInfo(name = "task_type")
    val taskType: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "processed_service_notifications",
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["session_epoch"]),
    ],
)
data class ProcessedServiceNotificationEntityDb(
    @PrimaryKey
    @ColumnInfo(name = "task_identity")
    val taskIdentity: String,
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "session_epoch")
    val sessionEpoch: Long,
    @ColumnInfo(name = "processed_at_millis")
    val processedAtMillis: Long,
)

@Entity(
    tableName = "initial_order_snapshots",
    primaryKeys = ["list_type", "order_id", "plan_id"],
    indices = [Index(value = ["order_id"])],
)
data class InitialOrderSnapshotEntityDb(
    @ColumnInfo(name = "list_type")
    val listType: Int,
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "state")
    val state: Int,
    @ColumnInfo(name = "elder_user_id")
    val elderUserId: Int,
    @ColumnInfo(name = "elder_name")
    val elderName: String,
    @ColumnInfo(name = "live_address")
    val liveAddress: String,
    @ColumnInfo(name = "start_time")
    val startTime: String,
    @ColumnInfo(name = "end_time")
    val endTime: String,
    @ColumnInfo(name = "refreshed_at_millis")
    val refreshedAtMillis: Long,
)
