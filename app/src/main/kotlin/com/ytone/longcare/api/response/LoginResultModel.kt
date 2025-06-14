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
    @field:Json("companyId")
    val companyId: Int = 0,

    /**
     * 账号Id
     */
    @field:Json("accountId")
    val accountId: Int = 0,

    /**
     * 用户Id
     */
    @field:Json("userId")
    val userId: Int = 0,

    /**
     * 用户名字
     */
    @field:Json("userName")
    val userName: String = "",

    /**
     * 头像地址
     */
    @field:Json("headUrl")
    val headUrl: String = "",

    /**
     * 用户身份 1护理员
     */
    @field:Json("userIdentity")
    val userIdentity: Int = 0,

    /**
     * 身份证号码
     */
    @field:Json("identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 性别 1男 0女
     */
    @field:Json("gender")
    val gender: Int = 0,

    /**
     * 用户身份token
     */
    @field:Json("token")
    val token: String = ""
)