package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 今天的服务订单
 */
@JsonClass(generateAdapter = true)
data class TodayServiceOrderModel(
    /**
     * 订单号
     */
    @field:Json("orderId")
    val orderId: Long = 0L,

    /**
     * 用户Id
     */
    @field:Json("userId")
    val userId: Int = 0,

    /**
     * 姓名
     */
    @field:Json("name")
    val name: String = "",

    /**
     * 老人联系电话
     */
    @field:Json("callPhone")
    val callPhone: String = "",

    /**
     * 身份证号
     */
    @field:Json("identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 居住地址
     */
    @field:Json("liveAddress")
    val liveAddress: String = "",

    /**
     * 状态:0待执行 1执行中 2任务完成 3作废
     */
    @field:Json("state")
    val state: Int = 0,

    /**
     * 中的服务时间
     */
    @field:Json("planTotalTime")
    val planTotalTime: Int = 0,

    /**
     * 时间完成时间
     */
    @field:Json("completeTotalTime")
    val completeTotalTime: Int = 0,

    /**
     * 服务时长
     */
    @field:Json("totalServiceTime")
    val totalServiceTime: Int = 0
)