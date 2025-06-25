package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 登录日志模型
 */
@JsonClass(generateAdapter = true)
data class LoginLogParamModel(
    /**
     * 手机系统
     */
    @param:Json(name = "phoneSystem")
    val phoneSystem: String = "",

    /**
     * 手机系统版本
     */
    @param:Json(name = "phoneVersion")
    val phoneVersion: String = "",

    /**
     * 网络类型
     */
    @param:Json(name = "networkType")
    val networkType: String = "",

    /**
     * 网络运营商
     */
    @param:Json(name = "networkOperator")
    val networkOperator: String = ""
)