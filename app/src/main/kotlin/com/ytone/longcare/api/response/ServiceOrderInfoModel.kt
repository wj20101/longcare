package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 服务订单详情
 */
@JsonClass(generateAdapter = true)
data class ServiceOrderInfoModel(
    /**
     * 服务单号
     */
    @Json(name = "orderId")
    val orderId: Long = 0L,

    /**
     * 服务状态
     */
    @Json(name = "state")
    val state: Int = 0,

    /**
     * 用户信息
     */
    @Json(name = "userInfo")
    val userInfo: UserInfoM = UserInfoM(),

    /**
     * 服务项目信息集合
     */
    @Json(name = "projectList")
    val projectList: List<ServiceProjectM> = emptyList()
)

/**
 * 用户信息
 */
@JsonClass(generateAdapter = true)
data class UserInfoM(
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
    val lastServiceTime: String = "", // Assuming date-time is represented as String

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

/**
 * 服务项目信息
 */
@JsonClass(generateAdapter = true)
data class ServiceProjectM(
    /**
     * 服务项目ID
     */
    @Json(name = "projectId")
    val projectId: Int = 0,

    /**
     * 服务项目名称
     */
    @Json(name = "projectName")
    val projectName: String = "",

    /**
     * 服务时长 分钟
     */
    @Json(name = "serviceTime")
    val serviceTime: Int = 0,

    /**
     * 最后服务的时间
     */
    @Json(name = "lastServiceTime")
    val lastServiceTime: String = "" // Assuming date-time is represented as String
)