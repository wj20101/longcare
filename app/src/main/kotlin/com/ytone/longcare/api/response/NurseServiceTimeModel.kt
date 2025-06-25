package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 本月服务统计信息
 */
@JsonClass(generateAdapter = true)
data class NurseServiceTimeModel(
    /**
     * 已服务时长
     */
    @param:Json(name = "haveServiceTime")
    val haveServiceTime: Int = 0,

    /**
     * 已经服务的次数
     */
    @param:Json(name = "haveServiceNum")
    val haveServiceNum: Int = 0,

    /**
     * 未服务时长
     */
    @param:Json(name = "noServiceTime")
    val noServiceTime: Int = 0
)