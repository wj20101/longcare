package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 发送短信验证码
 * @property mobile 手机号
 * @property codeType 验证码类型 1:登录
 */
@Serializable
data class SendSmsCodeParamModel(
    @SerialName("mobile")
    val mobile: String = "",
    @SerialName("codeType")
    val codeType: Int = 0
)