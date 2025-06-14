package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 登录结果
 */
@JsonClass(generateAdapter = true)
data class LoginResultModel(
    /**
     * 公司Id
     */
    @Json(name = "companyId")
    val companyId: Int = 0,

    /**
     * 账号Id
     */
    @Json(name = "accountId")
    val accountId: Int = 0,

    /**
     * 用户Id
     */
    @Json(name = "userId")
    val userId: Int = 0,

    /**
     * 用户名字
     */
    @Json(name = "userName")
    val userName: String = "",

    /**
     * 头像地址
     */
    @Json(name = "headUrl")
    val headUrl: String = "",

    /**
     * 用户身份 1护理员
     */
    @Json(name = "userIdentity")
    val userIdentity: Int = 0,

    /**
     * 身份证号码
     */
    @Json(name = "identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 性别 1男 0女
     */
    @Json(name = "gender")
    val gender: Int = 0,

    /**
     * 用户身份token
     */
    @Json(name = "token")
    val token: String = ""
)