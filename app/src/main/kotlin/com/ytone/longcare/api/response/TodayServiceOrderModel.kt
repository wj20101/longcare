package com.ytone.longcare.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 今天的服务订单
 */
@Serializable
data class TodayServiceOrderModel(
    /**
     * 订单号
     */
    @SerialName("orderId")
    val orderId: Long = 0L,

    /**
     * 用户Id
     */
    @SerialName("userId")
    val userId: Int = 0,

    /**
     * 姓名
     */
    @SerialName("name")
    val name: String = "",

    /**
     * 老人联系电话
     */
    @SerialName("callPhone")
    val callPhone: String = "",

    /**
     * 身份证号
     */
    @SerialName("identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 居住地址
     */
    @SerialName("liveAddress")
    val liveAddress: String = "",

    /**
     * 状态:0待执行 1执行中 2任务完成 3作废
     */
    @SerialName("state")
    val state: Int = 0,

    /**
     * 中的服务时间
     */
    @SerialName("planTotalTime")
    val planTotalTime: Int = 0,

    /**
     * 时间完成时间
     */
    @SerialName("completeTotalTime")
    val completeTotalTime: Int = 0,

    /**
     * 服务时长
     */
    @SerialName("totalServiceTime")
    val totalServiceTime: Int = 0
)