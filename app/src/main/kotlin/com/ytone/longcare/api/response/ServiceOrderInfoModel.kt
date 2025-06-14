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
    @field:Json("orderId")
    val orderId: Long = 0L,

    /**
     * 服务状态
     */
    @field:Json("state")
    val state: Int = 0,

    /**
     * 用户信息
     */
    @field:Json("userInfo")
    val userInfo: UserInfoM = UserInfoM(),

    /**
     * 服务项目信息集合
     */
    @field:Json("projectList")
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
    val lastServiceTime: String = "", // Assuming date-time is represented as String

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

/**
 * 服务项目信息
 */
@JsonClass(generateAdapter = true)
data class ServiceProjectM(
    /**
     * 服务项目ID
     */
    @field:Json("projectId")
    val projectId: Int = 0,

    /**
     * 服务项目名称
     */
    @field:Json("projectName")
    val projectName: String = "",

    /**
     * 服务时长 分钟
     */
    @field:Json("serviceTime")
    val serviceTime: Int = 0,

    /**
     * 最后服务的时间
     */
    @field:Json("lastServiceTime")
    val lastServiceTime: String = "" // Assuming date-time is represented as String
)