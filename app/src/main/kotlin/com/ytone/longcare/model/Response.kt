package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

private const val SUCCESS_CODE = 1000

@JsonClass(generateAdapter = true)
data class Response<T>(
    @Json(name = "resultCode") val resultCode: Int,
    @Json(name = "resultMsg") val resultMsg: String,
    @Json(name = "data") val data: T?,
) {
    fun isSuccess() = resultCode == SUCCESS_CODE
}

@JsonClass(generateAdapter = true)
data class ListResponse<T>(
    @Json(name = "data") val data: T, @Json(name = "hasNext") val hasNext: Int
)