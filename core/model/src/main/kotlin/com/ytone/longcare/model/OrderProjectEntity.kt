package com.ytone.longcare.model

/**
 * 订单项目关联表
 * 
 * 设计原则：
 * 1. 替代JSON字段存储项目列表，支持高效查询
 * 2. 服务端项目信息与本地选中状态分离
 * 3. 联合唯一索引确保数据完整性
 */
data class OrderProjectEntity(
    val id: Long = 0L,
    
    val orderId: Long,
    
    // ========== 服务端数据 ==========
    val projectId: Int,
    
    val projectName: String = "",
    
    val serviceTime: Int = 0,
    
    val lastServiceTime: String = "",
    
    val isComplete: Int = 0,
    
    // ========== 本地数据 ==========
    val isSelected: Boolean = false,
    
    // ========== 时间戳 ==========
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis()
)
