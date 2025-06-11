package com.ytone.longcare.model

import androidx.annotation.Keep

@Keep
data class Response<T>(
        val resultCode: Int,
        val resultMsg: String,
        val data: T,
) {
    fun isSuccess() = resultCode == 1000
}

@Keep
data class ListResponse<T>(
        val data: T, val hasNext: Int
)