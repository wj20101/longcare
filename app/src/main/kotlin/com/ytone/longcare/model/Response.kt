package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

private const val SUCCESS_CODE = 1000

@JsonClass(generateAdapter = true)
data class Response<T>(
    @param:Json(name = "resultCode") val resultCode: Int,
    @param:Json(name = "resultMsg") val resultMsg: String,
    @param:Json(name = "data") val data: T?,
) {
    fun isSuccess() = resultCode == SUCCESS_CODE
}

@JsonClass(generateAdapter = true)
data class ListResponse<T>(
    @param:Json(name = "data") val data: T, @param:Json(name = "hasNext") val hasNext: Int
)

/**
 * 是否是成功状态码
 */
fun Int.isSucceed() = this == SUCCESS_CODE