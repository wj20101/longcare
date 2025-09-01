package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.ytone.longcare.model.isCancelledState
import com.ytone.longcare.model.isNotStartedState
import com.ytone.longcare.model.isPendingCareState
import com.ytone.longcare.model.isServiceRecordState

/**
 * 今天的服务订单
 */
@JsonClass(generateAdapter = true)
data class TodayServiceOrderModel(
    /**
     * 订单号
     */
    @param:Json(name = "orderId")
    val orderId: Long = 0L,

    /**
     * 用户Id
     */
    @param:Json(name = "userId")
    val userId: Int = 0,

    /**
     * 姓名
     */
    @param:Json(name = "name")
    val name: String = "",

    /**
     * 老人联系电话
     */
    @param:Json(name = "callPhone")
    val callPhone: String = "",

    /**
     * 身份证号
     */
    @param:Json(name = "identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 居住地址
     */
    @param:Json(name = "liveAddress")
    val liveAddress: String = "",

    /**
     * 状态:0待执行 1执行中 2任务完成 3作废
     */
    @param:Json(name = "state")
    val state: Int = 0,

    /**
     * 中的服务时间
     */
    @param:Json(name = "planTotalTime")
    val planTotalTime: Int = 0,

    /**
     * 时间完成时间
     */
    @param:Json(name = "completeTotalTime")
    val completeTotalTime: Int = 0,

    /**
     * 服务时长
     */
    @param:Json(name = "totalServiceTime")
    val totalServiceTime: Int = 0
)

/**
 * 判断是否为待护理计划状态（待执行或执行中）
 */
fun TodayServiceOrderModel.isPendingCare(): Boolean {
    return state.isPendingCareState()
}

/**
 * 判断是否为未开单计划
 */
fun TodayServiceOrderModel.isNotStarted(): Boolean {
    return state.isNotStartedState()
}

/**
 * 判断是否为服务记录状态（任务完成）
 */
fun TodayServiceOrderModel.isServiceRecord(): Boolean {
    return state.isServiceRecordState()
}

/**
 * 判断是否为作废状态
 */
fun TodayServiceOrderModel.isCancelled(): Boolean {
    return state.isCancelledState()
}