package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单图片实体
 * 
 * 管理订单相关图片的上传状态，支持：
 * 1. 完整的状态机管理
 * 2. 上传失败重试
 * 3. 取消上传
 */
@Entity(
    tableName = "order_images",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
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
data class OrderImageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    
    // ========== 图片类型 ==========
    /**
     * 图片类型
     * @see ImageType
     */
    @ColumnInfo(name = "image_type")
    val imageType: Int,
    
    // ========== 本地信息 ==========
    @ColumnInfo(name = "local_uri")
    val localUri: String,
    
    @ColumnInfo(name = "local_path")
    val localPath: String? = null,
    
    // ========== 上传状态 ==========
    /**
     * 上传状态
     * @see ImageUploadStatus
     */
    @ColumnInfo(name = "upload_status", defaultValue = "0")
    val uploadStatus: Int = ImageUploadStatus.PENDING.value,
    
    @ColumnInfo(name = "cloud_key")
    val cloudKey: String? = null,
    
    @ColumnInfo(name = "cloud_url")
    val cloudUrl: String? = null,
    
    // ========== 错误信息 ==========
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    
    // ========== 时间戳 ==========
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取图片类型枚举
     */
    fun getImageTypeEnum(): ImageType = ImageType.fromValue(imageType)
    
    /**
     * 获取上传状态枚举
     */
    fun getUploadStatusEnum(): ImageUploadStatus = ImageUploadStatus.fromValue(uploadStatus)
}
