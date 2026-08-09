package com.ytone.longcare.model.result

/**
 * Repository operation result shared across data, domain, and presentation layers.
 *
 * Keeping this contract in the platform-free model module prevents domain APIs from
 * depending on networking or Android implementation details.
 */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()

    data class Failure(val code: Int, val message: String) : ApiResult<Nothing>()

    data class Exception(val exception: Throwable) : ApiResult<Nothing>()
}

fun <T> ApiResult<T>.isSuccess(): Boolean = this is ApiResult.Success

fun <T> ApiResult<T>.isFailure(): Boolean = this is ApiResult.Failure

fun <T> ApiResult<T>.isException(): Boolean = this is ApiResult.Exception
