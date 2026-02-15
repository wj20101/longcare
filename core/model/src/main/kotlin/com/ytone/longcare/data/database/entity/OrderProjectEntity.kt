package com.ytone.longcare.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单项目关联表
 * 
 * 设计原则：
 * 1. 替代JSON字段存储项目列表，支持高效查询
 * 2. 服务端项目信息与本地选中状态分离
 * 3. 联合唯一索引确保数据完整性
 */
@Entity(
    tableName = "order_projects",
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
        Index(value = ["order_id", "project_id"], unique = true),
        Index(value = ["is_selected"])
    ]
)
data class OrderProjectEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    
    // ========== 服务端数据 ==========
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
    
    // ========== 本地数据 ==========
    @ColumnInfo(name = "is_selected", defaultValue = "0")
    val isSelected: Boolean = false,
    
    // ========== 时间戳 ==========
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
