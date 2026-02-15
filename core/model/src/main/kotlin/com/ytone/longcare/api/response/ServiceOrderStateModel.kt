package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 服务订单状态响应模型
 */
@JsonClass(generateAdapter = true)
data class ServiceOrderStateModel(
    /**
     * 服务单号
     */
    @param:Json(name = "orderId")
    val orderId: Long = 0L,
    
    /**
     * 服务工单状态
     * -1: 未开单
     * 0: 待执行
     * 1: 执行中
     * 2: 任务完成
     * 3: 作废
     */
    @param:Json(name = "state")
    val state: Int = 0,
    
    /**
     * 状态说明
     */
    @param:Json(name = "stateDesc")
    val stateDesc: String? = null
) {
    companion object {
        /** 未开单 */
        const val STATE_NOT_CREATED = -1
        /** 待执行 */
        const val STATE_PENDING = 0
        /** 执行中 */
        const val STATE_IN_PROGRESS = 1
        /** 任务完成 */
        const val STATE_COMPLETED = 2
        /** 作废 */
        const val STATE_CANCELLED = 3
    }
    
    /**
     * 是否正在服务中
     */
    fun isInProgress(): Boolean = state == STATE_IN_PROGRESS
}
