package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单老人信息实体 - 存储老人相关信息
 * 
 * 设计原则：
 * 1. 与 [OrderEntity] 1:1 关联
 * 2. 老人信息独立存储，可单独更新
 * 3. 便于后续扩展老人相关属性
 */
@Entity(
    tableName = "order_elder_info",
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
        Index(value = ["elder_user_id"])
    ]
)
data class OrderElderInfoEntity(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    
    // ========== 老人基本信息 ==========
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
    
    // ========== 老人地址信息 ==========
    @ColumnInfo(name = "elder_address", defaultValue = "")
    val elderAddress: String = "",
    
    @ColumnInfo(name = "elder_lng", defaultValue = "")
    val elderLng: String = "",
    
    @ColumnInfo(name = "elder_lat", defaultValue = "")
    val elderLat: String = "",
    
    // ========== 服务统计信息 ==========
    @ColumnInfo(name = "last_service_time", defaultValue = "")
    val lastServiceTime: String = "",
    
    @ColumnInfo(name = "month_service_time", defaultValue = "0")
    val monthServiceTime: Int = 0,
    
    @ColumnInfo(name = "month_no_service_time", defaultValue = "0")
    val monthNoServiceTime: Int = 0,
    
    // ========== 时间戳 ==========
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
