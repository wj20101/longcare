package com.ytone.longcare.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 服务订单详情
 */
@Serializable
data class ServiceOrderInfoModel(
    /**
     * 服务单号
     */
    @SerialName("orderId")
    val orderId: Long = 0L,

    /**
     * 服务状态
     */
    @SerialName("state")
    val state: Int = 0,

    /**
     * 用户信息
     */
    @SerialName("userInfo")
    val userInfo: UserInfoM = UserInfoM(),

    /**
     * 服务项目信息集合
     */
    @SerialName("projectList")
    val projectList: List<ServiceProjectM> = emptyList()
)

/**
 * 用户信息
 */
@Serializable
data class UserInfoM(
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
    val lastServiceTime: String = "", // Assuming date-time is represented as String

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

/**
 * 服务项目信息
 */
@Serializable
data class ServiceProjectM(
    /**
     * 服务项目ID
     */
    @SerialName("projectId")
    val projectId: Int = 0,

    /**
     * 服务项目名称
     */
    @SerialName("projectName")
    val projectName: String = "",

    /**
     * 服务时长 分钟
     */
    @SerialName("serviceTime")
    val serviceTime: Int = 0,

    /**
     * 最后服务的时间
     */
    @SerialName("lastServiceTime")
    val lastServiceTime: String = "" // Assuming date-time is represented as String
)