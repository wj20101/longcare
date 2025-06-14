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
    @field:Json("phoneSystem")
    val phoneSystem: String = "",

    /**
     * 手机系统版本
     */
    @field:Json("phoneVersion")
    val phoneVersion: String = "",

    /**
     * 网络类型
     */
    @field:Json("networkType")
    val networkType: String = "",

    /**
     * 网络运营商
     */
    @field:Json("networkOperator")
    val networkOperator: String = ""
)