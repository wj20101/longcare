package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

private const val SUCCESS_CODE = 1000

@JsonClass(generateAdapter = true)
data class Response<T>(
    @field:Json("resultCode") val resultCode: Int,
    @field:Json("resultMsg") val resultMsg: String,
    @field:Json("data") val data: T,
) {
    fun isSuccess() = resultCode == SUCCESS_CODE
}

@JsonClass(generateAdapter = true)
data class ListResponse<T>(
    @field:Json("data") val data: T, @field:Json("hasNext") val hasNext: Int
)