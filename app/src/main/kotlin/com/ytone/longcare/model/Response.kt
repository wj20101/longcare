package com.ytone.longcare.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Response<T>(
    @SerialName("resultCode") val resultCode: Int,
    @SerialName("resultMsg") val resultMsg: String,
    @SerialName("data") val data: T,
) {
    fun isSuccess() = resultCode == 1000
}

@Serializable
data class ListResponse<T>(
    @SerialName("data") val data: T, @SerialName("hasNext") val hasNext: Int
)