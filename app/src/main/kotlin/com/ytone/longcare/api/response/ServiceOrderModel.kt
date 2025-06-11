package com.ytone.longcare.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 服务订单数据模型
 *
 * @property orderId 订单号
 * @property userId 用户Id
 * @property name 姓名
 * @property callPhone 老人联系电话
 * @property identityCardNumber 身份证号
 * @property liveAddress 居住地址
 * @property state 状态:0待执行 1执行中 2任务完成 3作废
 * @property planTotalTime 计划服务时间
 * @property completeTotalTime 时间完成时间
 * @property totalServiceTime 服务时长
 */
@Serializable
data class ServiceOrderModel(
    @SerialName("orderId") val orderId: Long = 0,
    @SerialName("userId") val userId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("callPhone") val callPhone: String = "",
    @SerialName("identityCardNumber") val identityCardNumber: String = "",
    @SerialName("liveAddress") val liveAddress: String = "",
    @SerialName("state") val state: Int = 0,
    @SerialName("planTotalTime") val planTotalTime: Int = 0,
    @SerialName("completeTotalTime") val completeTotalTime: Int = 0,
    @SerialName("totalServiceTime") val totalServiceTime: Int = 0
)