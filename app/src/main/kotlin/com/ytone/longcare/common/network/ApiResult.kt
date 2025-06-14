package com.ytone.longcare.common.network

/**
 * 一个通用的密封类，用于封装网络请求的结果。
 * @param T 成功时返回的数据类型。
 */
sealed class ApiResult<out T> {

    /**
     * 表示请求成功，并包含返回的数据。
     * @param data 从服务器成功获取的数据。
     */
    data class Success<out T>(val data: T) : ApiResult<T>()

    /**
     * 表示业务逻辑上的失败，例如服务器返回了错误的状态码。
     * @param code 业务错误码。
     * @param message 错误信息。
     */
    data class Failure(val code: Int, val message: String) : ApiResult<Nothing>()

    /**
     * 表示发生了网络异常或解析异常。
     * @param exception 捕获到的异常。
     */
    data class Exception(val exception: Throwable) : ApiResult<Nothing>()
}

/**
 * 判断是否是成功
 */
fun <T> ApiResult<T>.isSuccess(): Boolean = this is ApiResult.Success

/**
 * 判断是否是失败
 */
fun <T> ApiResult<T>.isFailure(): Boolean = this is ApiResult.Failure

/**
 * 判断是否是异常
 */
fun <T> ApiResult<T>.isException(): Boolean = this is ApiResult.Exception