package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 登录日志模型
 */
@Serializable
data class LoginLogParamModel(
    /**
     * 手机系统
     */
    @SerialName("phoneSystem")
    val phoneSystem: String = "",

    /**
     * 手机系统版本
     */
    @SerialName("phoneVersion")
    val phoneVersion: String = "",

    /**
     * 网络类型
     */
    @SerialName("networkType")
    val networkType: String = "",

    /**
     * 网络运营商
     */
    @SerialName("networkOperator")
    val networkOperator: String = ""
)