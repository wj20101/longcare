package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

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
@JsonClass(generateAdapter = true)
data class ServiceOrderModel(
    @Json(name = "orderId") val orderId: Long = 0,
    @Json(name = "userId") val userId: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "callPhone") val callPhone: String = "",
    @Json(name = "identityCardNumber") val identityCardNumber: String = "",
    @Json(name = "liveAddress") val liveAddress: String = "",
    @Json(name = "state") val state: Int = 0,
    @Json(name = "planTotalTime") val planTotalTime: Int = 0,
    @Json(name = "completeTotalTime") val completeTotalTime: Int = 0,
    @Json(name = "totalServiceTime") val totalServiceTime: Int = 0
)