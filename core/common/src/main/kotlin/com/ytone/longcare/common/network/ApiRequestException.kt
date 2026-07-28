package com.ytone.longcare.common.network

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * 数据层对外暴露的统一请求异常。
 *
 * [httpCode] 只在 [Kind.HTTP] 时有值。UI 通常只需要展示 [message]，
 * 诊断、重试策略等场景可以根据 [kind] 和 [httpCode] 做结构化判断。
 */
class ApiRequestException internal constructor(
    val kind: Kind,
    val httpCode: Int? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        CONNECTION,
        TIMEOUT,
        HTTP,
        INVALID_RESPONSE,
        UNKNOWN,
    }
}

internal object ApiRequestErrorMapper {

    fun fromHttp(
        code: Int,
        cause: Throwable? = null,
    ): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.HTTP,
            httpCode = code,
            message = httpMessage(code),
            cause = cause,
        )

    fun fromThrowable(throwable: Throwable): ApiRequestException =
        when (throwable) {
            is ApiRequestException -> throwable
            is HttpException -> fromHttp(throwable.code(), throwable)
            is SocketTimeoutException -> timeout(throwable)
            is InterruptedIOException ->
                if (throwable.message?.contains("timeout", ignoreCase = true) == true) {
                    timeout(throwable)
                } else {
                    connection(throwable)
                }

            is JsonDataException,
            is JsonEncodingException,
            is EOFException,
            -> invalidResponse(throwable)

            is IOException -> connection(throwable)
            else ->
                ApiRequestException(
                    kind = ApiRequestException.Kind.UNKNOWN,
                    message = "请求处理异常，请稍后重试",
                    cause = throwable,
                )
        }

    fun emptyBody(): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.INVALID_RESPONSE,
            message = "服务响应数据为空，请稍后重试",
        )

    private fun connection(cause: Throwable): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.CONNECTION,
            message = "网络连接异常，请检查您的网络",
            cause = cause,
        )

    private fun timeout(cause: Throwable): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.TIMEOUT,
            message = "请求超时，请稍后重试",
            cause = cause,
        )

    private fun invalidResponse(cause: Throwable): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.INVALID_RESPONSE,
            message = "服务响应数据异常，请稍后重试",
            cause = cause,
        )

    private fun httpMessage(code: Int): String =
        when (code) {
            400 -> "请求内容有误，请检查后重试"
            401 -> "登录状态已失效，请重新登录"
            403 -> "暂无权限执行此操作"
            404 -> "请求的服务不存在，请稍后重试"
            408 -> "请求超时，请稍后重试"
            409 -> "数据状态已变化，请刷新后重试"
            422 -> "请求内容无法处理，请检查后重试"
            429 -> "请求过于频繁，请稍后重试"
            in 500..599 -> "服务器暂时不可用，请稍后重试"
            else -> "请求失败（HTTP $code），请稍后重试"
        }
}
