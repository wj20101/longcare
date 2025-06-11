package com.ytone.longcare.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    /**
     * 公司Id
     */
    @SerialName("companyId")
    val companyId: Int = 0,

    /**
     * 账号Id
     */
    @SerialName("accountId")
    val accountId: Int = 0,

    /**
     * 用户Id
     */
    @SerialName("userId")
    val userId: Int = 0,

    /**
     * 用户名字
     */
    @SerialName("userName")
    val userName: String = "",

    /**
     * 头像地址
     */
    @SerialName("headUrl")
    val headUrl: String = "",

    /**
     * 用户身份 1护理员
     */
    @SerialName("userIdentity")
    val userIdentity: Int = 0,

    /**
     * 身份证号码
     */
    @SerialName("identityCardNumber")
    val identityCardNumber: String = "",

    /**
     * 性别 1男 0女
     */
    @SerialName("gender")
    val gender: Int = 0,

    /**
     * 用户身份token
     */
    @SerialName("token")
    val token: String = ""
)