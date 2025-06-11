package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 手机登录请求参数
 */
@Serializable
data class LoginPhoneParamModel(
    /**
     * 手机号码:纯号码不带86区号
     */
    @SerialName("mobile")
    val mobile: String = "",

    /**
     * 短信验证码
     */
    @SerialName("smsCode")
    val smsCode: String = "",

    /**
     * 身份1护理员
     */
    @SerialName("userIdentity")
    val userIdentity: Int = 0
)