package com.ytone.longcare.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 本月服务统计信息
 */
@Serializable
data class NurseServiceTimeModel(
    /**
     * 已服务时长
     */
    @SerialName("haveServiceTime")
    val haveServiceTime: Int = 0,

    /**
     * 已经服务的次数
     */
    @SerialName("haveServiceNum")
    val haveServiceNum: Int = 0,

    /**
     * 未服务时长
     */
    @SerialName("noServiceTime")
    val noServiceTime: Int = 0
)