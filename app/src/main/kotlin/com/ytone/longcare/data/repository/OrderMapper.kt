package com.ytone.longcare.data.repository

import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.api.response.UserInfoM
import com.ytone.longcare.data.database.entity.OrderElderInfoEntity
import com.ytone.longcare.data.database.entity.OrderEntity
import com.ytone.longcare.data.database.entity.OrderProjectEntity

/**
 * API响应模型到数据库实体的映射器
 */
object OrderMapper {
    
    /**
     * 将API响应的订单详情转换为OrderEntity
     */
    fun ServiceOrderInfoModel.toOrderEntity(): OrderEntity {
        return OrderEntity(
            orderId = orderId,
            planId = 0, // API暂未提供planId
            state = state,
            startTime = startTime,
            endTime = endTime,
            lastSyncTime = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 将API响应的用户信息转换为OrderElderInfoEntity
     */
    fun UserInfoM.toOrderElderInfoEntity(orderId: Long): OrderElderInfoEntity {
        return OrderElderInfoEntity(
            orderId = orderId,
            elderUserId = userId,
            elderName = name,
            elderIdCard = identityCardNumber,
            elderAge = age,
            elderGender = gender,
            elderAddress = address,
            elderLng = lng,
            elderLat = lat,
            lastServiceTime = lastServiceTime,
            monthServiceTime = monthServiceTime,
            monthNoServiceTime = monthNoServiceTime,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 将API响应的服务项目转换为OrderProjectEntity
     */
    fun ServiceProjectM.toOrderProjectEntity(orderId: Long): OrderProjectEntity {
        return OrderProjectEntity(
            orderId = orderId,
            projectId = projectId,
            projectName = projectName,
            serviceTime = serviceTime,
            lastServiceTime = lastServiceTime,
            isComplete = isComplete,
            isSelected = false, // 默认未选中
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 将API响应的服务项目列表转换为OrderProjectEntity列表
     */
    fun List<ServiceProjectM>.toOrderProjectEntities(orderId: Long): List<OrderProjectEntity> {
        return map { it.toOrderProjectEntity(orderId) }
    }
}
