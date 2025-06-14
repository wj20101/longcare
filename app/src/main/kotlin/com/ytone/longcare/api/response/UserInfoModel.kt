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
    @Json(name = "userId")
    val userId: Int = 0,

    /**
     * 老人姓名
     */
    @Json(name = "name")
    val name: String = "",

    /**
     * 身份证号码
     */
    @Json(name = "identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 年龄
     */
    @Json(name = "age")
    val age: Int = 0,

    /**
     * 性别
     */
    @Json(name = "gender")
    val gender: String = "",

    /**
     * 居住地址
     */
    @Json(name = "address")
    val address: String = "",

    /**
     * 最后服务时间
     */
    @Json(name = "lastServiceTime")
    val lastServiceTime: String = "",

    /**
     * 本月已服务时间
     */
    @Json(name = "monthServiceTime")
    val monthServiceTime: Int = 0,

    /**
     * 本月未服务时间
     */
    @Json(name = "monthNoServiceTime")
    val monthNoServiceTime: Int = 0
)