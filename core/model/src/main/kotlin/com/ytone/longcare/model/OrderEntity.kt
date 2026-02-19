package com.ytone.longcare.model

/**
 * 订单主实体 - 仅订单核心信息
 * 
 * 设计原则：
 * 1. 只包含订单本身的核心属性
 * 2. 老人信息拆分到 [OrderElderInfoEntity]
 * 3. 本地状态拆分到 [OrderLocalStateEntity]
 * 4. 便于后续各部分独立更新和维护
 */
data class OrderEntity(
    val orderId: Long,
    
    // ========== 订单基本信息 ==========
    val planId: Int = 0,
    
    val state: Int = 0,
    
    val startTime: String = "",
    
    val endTime: String = "",
    
    // ========== 同步元数据 ==========
    val lastSyncTime: Long = 0L,
    
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis()
)
