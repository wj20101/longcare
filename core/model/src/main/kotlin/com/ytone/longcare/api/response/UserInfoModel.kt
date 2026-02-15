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
    @param:Json(name = "userId")
    val userId: Int = 0,

    /**
     * 老人姓名
     */
    @param:Json(name = "name")
    val name: String = "",

    /**
     * 身份证号码
     */
    @param:Json(name = "identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 年龄
     */
    @param:Json(name = "age")
    val age: Int = 0,

    /**
     * 性别
     */
    @param:Json(name = "gender")
    val gender: String = "",

    /**
     * 居住地址
     */
    @param:Json(name = "address")
    val address: String = "",

    /**
     * 最后服务时间
     */
    @param:Json(name = "lastServiceTime")
    val lastServiceTime: String = "",

    /**
     * 本月已服务时间
     */
    @param:Json(name = "monthServiceTime")
    val monthServiceTime: Int = 0,

    /**
     * 本月未服务时间
     */
    @param:Json(name = "monthNoServiceTime")
    val monthNoServiceTime: Int = 0
)