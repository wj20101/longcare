package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 服务订单数据模型
 *
 * @property orderId 订单号
 * @property planId 护理计划Id
 * @property userId 用户Id
 * @property name 姓名
 * @property callPhone 老人联系电话
 * @property identityCardNumber 身份证号
 * @property liveAddress 居住地址
 * @property state 状态:0待执行 1执行中 2任务完成 3作废
 * @property checkState 检测状态 1才能开单
 * @property checkStateDesc 检测状态描述
 * @property planTotalTime 计划服务时间
 * @property completeTotalTime 时间完成时间
 * @property totalServiceTime 服务时长
 * @property longitude 经度
 * @property latitude 纬度
 * @property startTime 服务开始时间
 * @property endTime 服务结束时间
 */
@JsonClass(generateAdapter = true)
data class ServiceOrderModel(
    @param:Json(name = "orderId") val orderId: Long = 0,
    @param:Json(name = "planId") val planId: Int = 0,
    @param:Json(name = "userId") val userId: Int = 0,
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "callPhone") val callPhone: String = "",
    @param:Json(name = "identityCardNumber") val identityCardNumber: String = "",
    @param:Json(name = "liveAddress") val liveAddress: String = "",
    @param:Json(name = "state") val state: Int = 0,
    @param:Json(name = "checkState") val checkState: Int = 0,
    @param:Json(name = "checkStateDesc") val checkStateDesc: String = "",
    @param:Json(name = "planTotalTime") val planTotalTime: Int = 0,
    @param:Json(name = "completeTotalTime") val completeTotalTime: Int = 0,
    @param:Json(name = "totalServiceTime") val totalServiceTime: Int = 0,
    @param:Json(name = "longitude") val longitude: String = "",
    @param:Json(name = "latitude") val latitude: String = "",
    @param:Json(name = "startTime") val startTime: String = "",
    @param:Json(name = "endTime") val endTime: String = ""
)