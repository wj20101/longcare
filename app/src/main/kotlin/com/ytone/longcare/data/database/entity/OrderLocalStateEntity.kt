package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单本地状态实体 - 存储客户端维护的本地数据
 * 
 * 设计原则：
 * 1. 与 [OrderEntity] 1:1 关联
 * 2. 仅存储本地状态，不包含服务端数据
 * 3. 便于服务端数据整体覆盖更新时不影响本地状态
 */
@Entity(
    tableName = "order_local_states",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
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
data class OrderLocalStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    
    // ========== 本地服务状态 ==========
    /**
     * 本地订单状态
     * @see LocalOrderStatus
     */
    @ColumnInfo(name = "local_status", defaultValue = "0")
    val localStatus: Int = LocalOrderStatus.PENDING.value,
    
    @ColumnInfo(name = "local_start_timestamp")
    val localStartTimestamp: Long? = null,
    
    @ColumnInfo(name = "local_end_timestamp")
    val localEndTimestamp: Long? = null,
    
    // ========== 人脸验证状态 ==========
    @ColumnInfo(name = "face_verification_completed", defaultValue = "0")
    val faceVerificationCompleted: Boolean = false,
    
    // ========== 同步标记 ==========
    @ColumnInfo(name = "needs_sync", defaultValue = "0")
    val needsSync: Boolean = false,
    
    // ========== 时间戳 ==========
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取本地状态枚举
     */
    fun getLocalStatusEnum(): LocalOrderStatus = LocalOrderStatus.fromValue(localStatus)
}
