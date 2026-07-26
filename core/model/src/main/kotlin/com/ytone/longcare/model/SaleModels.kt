package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 获取俏郎中检测 Token 的请求参数。
 */
@JsonClass(generateAdapter = true)
data class GetCheckTokenParamModel(
    @param:Json(name = "id")
    val id: Int,
    @param:Json(name = "checkDeviceId")
    val checkDeviceId: String,
)

/**
 * 可注入俏郎中 SDK 的一次性检测 Token。
 */
@JsonClass(generateAdapter = true)
data class CheckTokenModel(
    @param:Json(name = "token")
    val token: String = "",
    @param:Json(name = "tokenType")
    val tokenType: Int = 0,
    @param:Json(name = "expireAt")
    val expireAt: Long = 0L,
    @param:Json(name = "bizType")
    val bizType: Int = 0,
)

/**
 * 新增潜在客户的请求参数。
 */
@JsonClass(generateAdapter = true)
data class AddUserLatentParamModel(
    @param:Json(name = "userName")
    val userName: String = "",
    @param:Json(name = "identityCardNumber")
    val identityCardNumber: String = "",
    @param:Json(name = "guardianName")
    val guardianName: String = "",
    @param:Json(name = "guardianPhone")
    val guardianPhone: String = "",
    @param:Json(name = "guardianRelation")
    val guardianRelation: String = "",
    @param:Json(name = "liveAddress")
    val liveAddress: String = "",
    @param:Json(name = "liveLng")
    val liveLng: String = "",
    @param:Json(name = "liveLat")
    val liveLat: String = "",
    @param:Json(name = "img1")
    val img1: String = "",
    @param:Json(name = "img2")
    val img2: String = "",
    @param:Json(name = "img3")
    val img3: String = "",
)

/**
 * 新增潜在客户后的标识与评估页地址。
 */
@JsonClass(generateAdapter = true)
data class AddUserLatentResultModel(
    @param:Json(name = "id")
    val id: Int = 0,
    @param:Json(name = "pgUrl")
    val pgUrl: String = "",
)

/**
 * 搜索潜在客户的筛选条件。
 */
@JsonClass(generateAdapter = true)
data class SearchUserLatentParamModel(
    @param:Json(name = "userName")
    val userName: String = "",
    @param:Json(name = "checkState")
    val checkState: Int = UserLatentCheckState.ALL,
)

/**
 * 潜在客户列表项。
 */
@JsonClass(generateAdapter = true)
data class UserLatentListModel(
    @param:Json(name = "id")
    val id: Int = 0,
    @param:Json(name = "userName")
    val userName: String = "",
    @param:Json(name = "checkState")
    val checkState: Int = UserLatentCheckState.NOT_SUBMITTED,
    @param:Json(name = "liveAddress")
    val liveAddress: String = "",
    @param:Json(name = "identityCardNumber")
    val identityCardNumber: String = "",
)

/**
 * 潜在客户详情。
 */
@JsonClass(generateAdapter = true)
data class UserLatentDetailModel(
    @param:Json(name = "id")
    val id: Int = 0,
    @param:Json(name = "userName")
    val userName: String = "",
    @param:Json(name = "identityCardNumber")
    val identityCardNumber: String = "",
    @param:Json(name = "guardianName")
    val guardianName: String = "",
    @param:Json(name = "guardianPhone")
    val guardianPhone: String = "",
    @param:Json(name = "guardianRelation")
    val guardianRelation: String = "",
    @param:Json(name = "liveAddress")
    val liveAddress: String = "",
    @param:Json(name = "liveLng")
    val liveLng: String = "",
    @param:Json(name = "liveLat")
    val liveLat: String = "",
    @param:Json(name = "img1")
    val img1: String = "",
    @param:Json(name = "img2")
    val img2: String = "",
    @param:Json(name = "img3")
    val img3: String = "",
    @param:Json(name = "checkStatus")
    val checkStatus: Int = UserLatentCheckState.NOT_SUBMITTED,
    @param:Json(name = "checkTime")
    val checkTime: String = "",
    @param:Json(name = "createTime")
    val createTime: String = "",
    @param:Json(name = "pgId")
    val pgId: Int = 0,
    @param:Json(name = "pgResult")
    val pgResult: String = "",
    @param:Json(name = "pgUrl")
    val pgUrl: String = "",
)

object UserLatentCheckState {
    const val ALL = -1
    const val NOT_SUBMITTED = 0
    const val PENDING_REVIEW = 1
    const val APPROVED = 2
    const val REJECTED = 3

    fun label(value: Int): String =
        when (value) {
            ALL -> "全部"
            NOT_SUBMITTED -> "未申报"
            PENDING_REVIEW -> "待审核"
            APPROVED -> "审核通过"
            REJECTED -> "审核被拒绝"
            else -> "未知($value)"
        }
}
