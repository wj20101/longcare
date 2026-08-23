package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ytone.longcare.model.ImageUploadStatus
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.LocalOrderStatus

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["state"])
    ]
)
data class OrderEntityDb(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "plan_id", defaultValue = "0")
    val planId: Int = 0,
    @ColumnInfo(name = "state", defaultValue = "0")
    val state: Int = 0,
    @ColumnInfo(name = "start_time", defaultValue = "")
    val startTime: String = "",
    @ColumnInfo(name = "end_time", defaultValue = "")
    val endTime: String = "",
    @ColumnInfo(name = "last_sync_time", defaultValue = "0")
    val lastSyncTime: Long = 0L,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "order_elder_info",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntityDb::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"], unique = true),
        Index(value = ["elder_user_id"])
    ]
)
data class OrderElderInfoEntityDb(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "elder_user_id", defaultValue = "0")
    val elderUserId: Int = 0,
    @ColumnInfo(name = "elder_name", defaultValue = "")
    val elderName: String = "",
    @ColumnInfo(name = "elder_id_card", defaultValue = "")
    val elderIdCard: String = "",
    @ColumnInfo(name = "elder_age", defaultValue = "0")
    val elderAge: Int = 0,
    @ColumnInfo(name = "elder_gender", defaultValue = "")
    val elderGender: String = "",
    @ColumnInfo(name = "elder_address", defaultValue = "")
    val elderAddress: String = "",
    @ColumnInfo(name = "elder_lng", defaultValue = "")
    val elderLng: String = "",
    @ColumnInfo(name = "elder_lat", defaultValue = "")
    val elderLat: String = "",
    @ColumnInfo(name = "last_service_time", defaultValue = "")
    val lastServiceTime: String = "",
    @ColumnInfo(name = "month_service_time", defaultValue = "0")
    val monthServiceTime: Int = 0,
    @ColumnInfo(name = "month_no_service_time", defaultValue = "0")
    val monthNoServiceTime: Int = 0,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "order_local_states",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntityDb::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"], unique = true),
        Index(value = ["local_status"])
    ]
)
data class OrderLocalStateEntityDb(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "local_status", defaultValue = "0")
    val localStatus: Int = LocalOrderStatus.PENDING.value,
    @ColumnInfo(name = "local_start_timestamp")
    val localStartTimestamp: Long? = null,
    @ColumnInfo(name = "local_end_timestamp")
    val localEndTimestamp: Long? = null,
    @ColumnInfo(name = "face_verification_completed", defaultValue = "0")
    val faceVerificationCompleted: Boolean = false,
    @ColumnInfo(name = "needs_sync", defaultValue = "0")
    val needsSync: Boolean = false,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "order_projects",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntityDb::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["order_id", "project_id"], unique = true),
        Index(value = ["is_selected"])
    ]
)
data class OrderProjectEntityDb(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "project_id")
    val projectId: Int,
    @ColumnInfo(name = "project_name", defaultValue = "")
    val projectName: String = "",
    @ColumnInfo(name = "service_time", defaultValue = "0")
    val serviceTime: Int = 0,
    @ColumnInfo(name = "last_service_time", defaultValue = "")
    val lastServiceTime: String = "",
    @ColumnInfo(name = "is_complete", defaultValue = "0")
    val isComplete: Int = 0,
    @ColumnInfo(name = "is_selected", defaultValue = "0")
    val isSelected: Boolean = false,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "order_images",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntityDb::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["order_id", "image_type"]),
        Index(value = ["upload_status"])
    ]
)
data class OrderImageEntityDb(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "image_type")
    val imageType: Int,
    @ColumnInfo(name = "local_uri")
    val localUri: String,
    @ColumnInfo(name = "local_path")
    val localPath: String? = null,
    @ColumnInfo(name = "upload_status", defaultValue = "0")
    val uploadStatus: Int = ImageUploadStatus.PENDING.value,
    @ColumnInfo(name = "cloud_key")
    val cloudKey: String? = null,
    @ColumnInfo(name = "cloud_url")
    val cloudUrl: String? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getImageTypeEnum(): ImageType = ImageType.fromValue(imageType)

    fun getUploadStatusEnum(): ImageUploadStatus = ImageUploadStatus.fromValue(uploadStatus)
}
