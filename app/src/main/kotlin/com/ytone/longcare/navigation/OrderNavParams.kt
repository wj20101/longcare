package com.ytone.longcare.navigation

import androidx.annotation.Keep
import com.ytone.longcare.api.request.OrderInfoRequestModel
import kotlinx.serialization.Serializable

/**
 * 订单导航参数模型
 * 用于在导航中传递订单相关信息
 * 
 * @param orderId 订单ID
 * @param planId 计划ID，默认值为0
 */
@Keep
@Serializable
data class OrderNavParams(
    val orderId: Long,
    val planId: Int = 0
)

/**
 * 转换为订单信息请求模型
 */
fun OrderNavParams.toRequestModel() = OrderInfoRequestModel(
    orderId = orderId,
    planId = planId
)
