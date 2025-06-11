package com.ytone.longcare.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户信息
 */
@Serializable
data class UserInfoModel(
    /**
     * 用户Id
     */
    @SerialName("userId")
    val userId: Int = 0,

    /**
     * 老人姓名
     */
    @SerialName("name")
    val name: String = "",

    /**
     * 身份证号码
     */
    @SerialName("identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 年龄
     */
    @SerialName("age")
    val age: Int = 0,

    /**
     * 性别
     */
    @SerialName("gender")
    val gender: String = "",

    /**
     * 居住地址
     */
    @SerialName("address")
    val address: String = "",

    /**
     * 最后服务时间
     */
    @SerialName("lastServiceTime")
    val lastServiceTime: String = "",

    /**
     * 本月已服务时间
     */
    @SerialName("monthServiceTime")
    val monthServiceTime: Int = 0,

    /**
     * 本月未服务时间
     */
    @SerialName("monthNoServiceTime")
    val monthNoServiceTime: Int = 0
)