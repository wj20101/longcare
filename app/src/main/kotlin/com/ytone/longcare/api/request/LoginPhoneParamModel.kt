package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 手机登录请求参数
 */
@JsonClass(generateAdapter = true)
data class LoginPhoneParamModel(
    /**
     * 手机号码:纯号码不带86区号
     */
    @param:Json(name = "mobile")
    val mobile: String = "",

    /**
     * 短信验证码
     */
    @param:Json(name = "smsCode")
    val smsCode: String = "",

    /**
     * 身份1护理员
     */
    @param:Json(name = "userIdentity")
    val userIdentity: Int = 0
)