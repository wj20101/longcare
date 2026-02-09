package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单定位记录实体
 * 
 * 存储服务过程中的位置记录，用于：
 * 1. 轨迹记录
 * 2. 服务区域验证
 * 3. 位置上报
 */
@Entity(
    tableName = "order_locations",
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
        Index(value = ["upload_status"])
    ]
)
data class OrderLocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    
    // ========== 位置信息 ==========
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    
    @ColumnInfo(name = "accuracy", defaultValue = "0")
    val accuracy: Float = 0f,
    
    @ColumnInfo(name = "provider", defaultValue = "")
    val provider: String = "",
    
    // ========== 上传状态 ==========
    /**
     * 上传状态
     * @see LocationUploadStatus
     */
    @ColumnInfo(name = "upload_status", defaultValue = "0")
    val uploadStatus: Int = LocationUploadStatus.PENDING.value,
    
    // ========== 时间戳 ==========
    @ColumnInfo(name = "timestamp", defaultValue = "0")
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 获取上传状态枚举
     */
    fun getUploadStatusEnum(): LocationUploadStatus = LocationUploadStatus.fromValue(uploadStatus)
}
