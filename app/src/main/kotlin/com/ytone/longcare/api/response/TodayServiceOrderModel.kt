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
    @Json(name = "orderId")
    val orderId: Long = 0L,

    /**
     * 用户Id
     */
    @Json(name = "userId")
    val userId: Int = 0,

    /**
     * 姓名
     */
    @Json(name = "name")
    val name: String = "",

    /**
     * 老人联系电话
     */
    @Json(name = "callPhone")
    val callPhone: String = "",

    /**
     * 身份证号
     */
    @Json(name = "identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 居住地址
     */
    @Json(name = "liveAddress")
    val liveAddress: String = "",

    /**
     * 状态:0待执行 1执行中 2任务完成 3作废
     */
    @Json(name = "state")
    val state: Int = 0,

    /**
     * 中的服务时间
     */
    @Json(name = "planTotalTime")
    val planTotalTime: Int = 0,

    /**
     * 时间完成时间
     */
    @Json(name = "completeTotalTime")
    val completeTotalTime: Int = 0,

    /**
     * 服务时长
     */
    @Json(name = "totalServiceTime")
    val totalServiceTime: Int = 0
)