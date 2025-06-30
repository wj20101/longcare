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
    @param:Json(name = "orderId")
    val orderId: Long = 0L,

    /**
     * 服务状态
     */
    @param:Json(name = "state")
    val state: Int = 0,

    /**
     * 用户信息
     */
    @param:Json(name = "userInfo")
    val userInfo: UserInfoM = UserInfoM(),

    /**
     * 服务项目信息集合
     */
    @param:Json(name = "projectList")
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

/**
 * 服务项目信息
 */
@JsonClass(generateAdapter = true)
data class ServiceProjectM(
    /**
     * 服务项目ID
     */
    @param:Json(name = "projectId")
    val projectId: Int = 0,

    /**
     * 服务项目名称
     */
    @param:Json(name = "projectName")
    val projectName: String = "",

    /**
     * 服务时长 分钟
     */
    @param:Json(name = "serviceTime")
    val serviceTime: Int = 0,

    /**
     * 最后服务的时间
     */
    @param:Json(name = "lastServiceTime")
    val lastServiceTime: String = ""
)