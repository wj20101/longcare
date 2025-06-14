package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 用户信息
 */
@JsonClass(generateAdapter = true)
data class UserInfoModel(
    /**
     * 用户Id
     */
    @field:Json("userId")
    val userId: Int = 0,

    /**
     * 老人姓名
     */
    @field:Json("name")
    val name: String = "",

    /**
     * 身份证号码
     */
    @field:Json("identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 年龄
     */
    @field:Json("age")
    val age: Int = 0,

    /**
     * 性别
     */
    @field:Json("gender")
    val gender: String = "",

    /**
     * 居住地址
     */
    @field:Json("address")
    val address: String = "",

    /**
     * 最后服务时间
     */
    @field:Json("lastServiceTime")
    val lastServiceTime: String = "",

    /**
     * 本月已服务时间
     */
    @field:Json("monthServiceTime")
    val monthServiceTime: Int = 0,

    /**
     * 本月未服务时间
     */
    @field:Json("monthNoServiceTime")
    val monthNoServiceTime: Int = 0
)