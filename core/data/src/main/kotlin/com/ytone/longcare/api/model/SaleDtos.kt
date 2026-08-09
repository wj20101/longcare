package com.ytone.longcare.api.model

import com.squareup.moshi.JsonClass

/** Network-only request/response types for /V1/Sale. */
@JsonClass(generateAdapter = true)
data class GetCheckTokenRequestDto(
    val id: Int,
    val checkDeviceId: String?,
)

@JsonClass(generateAdapter = true)
data class CheckTokenDto(
    val token: String? = null,
    val tokenType: Int = 0,
    val expireAt: Long = 0L,
    val bizType: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AddUserLatentRequestDto(
    val userName: String? = "",
    val identityCardNumber: String? = "",
    val guardianName: String? = "",
    val guardianPhone: String? = "",
    val guardianRelation: String? = "",
    val liveAddress: String? = "",
    val liveLng: String? = "",
    val liveLat: String? = "",
    val img1: String? = "",
    val img2: String? = "",
    val img3: String? = "",
)

@JsonClass(generateAdapter = true)
data class AddUserLatentResponseDto(
    val id: Int = 0,
    val pgUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class SearchUserLatentRequestDto(
    val pageIndex: Int = 1,
    val userName: String? = "",
    val checkState: Int = -1,
)

@JsonClass(generateAdapter = true)
data class UserLatentListDto(
    val id: Int = 0,
    val userName: String? = null,
    val checkState: Int = 0,
    val liveAddress: String? = null,
    val identityCardNumber: String? = null,
)

@JsonClass(generateAdapter = true)
data class ToDoCountDto(
    val num: Int = 0,
)

@JsonClass(generateAdapter = true)
data class ToDoItemDto(
    val title: String? = null,
    val content: String? = null,
    val createTime: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserLatentDetailDto(
    val id: Int = 0,
    val userName: String? = null,
    val identityCardNumber: String? = null,
    val guardianName: String? = null,
    val guardianPhone: String? = null,
    val guardianRelation: String? = null,
    val liveAddress: String? = null,
    val liveLng: String? = null,
    val liveLat: String? = null,
    val img1: String? = null,
    val img2: String? = null,
    val img3: String? = null,
    val checkStatus: Int = 0,
    val checkTime: String? = null,
    val checkDesc: String? = null,
    val createTime: String? = null,
    val pgId: Int = 0,
    val pgResult: String? = null,
    val pgScore: Int = 0,
    val pgUrl: String? = null,
)
