package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 发送短信验证码
 * @property mobile 手机号
 * @property codeType 验证码类型 1:登录
 */
@JsonClass(generateAdapter = true)
data class SendSmsCodeParamModel(
    @Json(name = "mobile")
    val mobile: String = "",
    @Json(name = "codeType")
    val codeType: Int = 0
)