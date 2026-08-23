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
 * [httpCode] 只在 [Kind.HTTP] 时有值。[message] 只用于诊断；展示层应根据 [kind]
 * 解析本地化资源，不得将异常文案直接暴露给用户。
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
                    message = "Unexpected API request failure",
                    cause = throwable,
                )
        }

    fun emptyBody(): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.INVALID_RESPONSE,
            message = "API response body was empty",
        )

    fun emptyData(): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.INVALID_RESPONSE,
            message = "Successful API response contained no data",
        )

    private fun connection(cause: Throwable): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.CONNECTION,
            message = "API connection failed",
            cause = cause,
        )

    private fun timeout(cause: Throwable): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.TIMEOUT,
            message = "API request timed out",
            cause = cause,
        )

    private fun invalidResponse(cause: Throwable): ApiRequestException =
        ApiRequestException(
            kind = ApiRequestException.Kind.INVALID_RESPONSE,
            message = "API response could not be decoded",
            cause = cause,
        )

    private fun httpMessage(code: Int): String = "API HTTP request failed (status=$code)"
}
