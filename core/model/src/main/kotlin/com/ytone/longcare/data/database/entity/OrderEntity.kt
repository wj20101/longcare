package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单主实体 - 仅订单核心信息
 * 
 * 设计原则：
 * 1. 只包含订单本身的核心属性
 * 2. 老人信息拆分到 [OrderElderInfoEntity]
 * 3. 本地状态拆分到 [OrderLocalStateEntity]
 * 4. 便于后续各部分独立更新和维护
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["state"])
    ]
)
data class OrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    
    // ========== 订单基本信息 ==========
    @ColumnInfo(name = "plan_id", defaultValue = "0")
    val planId: Int = 0,
    
    @ColumnInfo(name = "state", defaultValue = "0")
    val state: Int = 0,
    
    @ColumnInfo(name = "start_time", defaultValue = "")
    val startTime: String = "",
    
    @ColumnInfo(name = "end_time", defaultValue = "")
    val endTime: String = "",
    
    // ========== 同步元数据 ==========
    @ColumnInfo(name = "last_sync_time", defaultValue = "0")
    val lastSyncTime: Long = 0L,
    
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
