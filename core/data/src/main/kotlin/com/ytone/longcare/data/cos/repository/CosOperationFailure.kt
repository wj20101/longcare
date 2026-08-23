package com.ytone.longcare.data.cos.repository

import com.tencent.cos.xml.common.ClientErrorCode
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.CosStorageException
import com.ytone.longcare.model.CosStorageFailureKind
import kotlinx.coroutines.CancellationException

internal fun Throwable.toCosStorageException(): CosStorageException =
    when (this) {
        is CosStorageException -> this
        is CosXmlServiceException -> {
            val status = statusCode
            when {
                status == 404 ->
                    CosStorageException(
                        kind = CosStorageFailureKind.NOT_FOUND,
                        errorCode = "COS_HTTP_404",
                        retryable = false,
                        diagnosticMessage = "COS object was not found (HTTP 404)",
                        cause = this,
                    )

                status == 401 || status == 403 ->
                    CosStorageException(
                        kind = CosStorageFailureKind.AUTHORIZATION,
                        errorCode = "COS_HTTP_$status",
                        retryable = true,
                        diagnosticMessage = "COS authorization failed (HTTP $status)",
                        cause = this,
                    )

                status == 408 || status == 429 || status in 500..599 ->
                    CosStorageException(
                        kind = CosStorageFailureKind.SERVICE,
                        errorCode = "COS_HTTP_$status",
                        retryable = true,
                        diagnosticMessage = "COS service is temporarily unavailable (HTTP $status)",
                        cause = this,
                    )

                else ->
                    CosStorageException(
                        kind = CosStorageFailureKind.SERVICE,
                        errorCode =
                            errorCode
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "COS_$it" }
                                ?: "COS_HTTP_$status",
                        retryable = false,
                        diagnosticMessage = "COS service request failed (HTTP $status)",
                        cause = this,
                    )
            }
        }

        is CosXmlClientException -> {
            val isRetryable =
                errorCode in
                    setOf(
                        ClientErrorCode.INVALID_CREDENTIALS.code,
                        ClientErrorCode.INTERNAL_ERROR.code,
                        ClientErrorCode.SERVERERROR.code,
                        ClientErrorCode.IO_ERROR.code,
                        ClientErrorCode.POOR_NETWORK.code,
                    )
            CosStorageException(
                kind =
                    if (errorCode == ClientErrorCode.POOR_NETWORK.code) {
                        CosStorageFailureKind.NETWORK
                    } else {
                        CosStorageFailureKind.CLIENT
                    },
                errorCode = "COS_CLIENT_$errorCode",
                retryable = isRetryable,
                diagnosticMessage = "COS client request failed (code=$errorCode)",
                cause = this,
            )
        }

        is ApiRequestException ->
            CosStorageException(
                kind =
                    when (kind) {
                        ApiRequestException.Kind.CONNECTION,
                        ApiRequestException.Kind.TIMEOUT,
                        -> CosStorageFailureKind.NETWORK

                        ApiRequestException.Kind.INVALID_RESPONSE ->
                            CosStorageFailureKind.INVALID_RESPONSE

                        ApiRequestException.Kind.HTTP -> CosStorageFailureKind.BACKEND
                        ApiRequestException.Kind.UNKNOWN -> CosStorageFailureKind.UNKNOWN
                    },
                errorCode =
                    httpCode?.let { "HTTP_$it" }
                        ?: "API_${kind.name}",
                retryable =
                    kind == ApiRequestException.Kind.CONNECTION ||
                        kind == ApiRequestException.Kind.TIMEOUT ||
                        httpCode == 408 ||
                        httpCode == 429 ||
                        httpCode in 500..599,
                diagnosticMessage = "File API request failed (${kind.name})",
                cause = this,
            )

        else ->
            CosStorageException(
                kind = CosStorageFailureKind.UNKNOWN,
                errorCode = "COS_UNKNOWN",
                retryable = false,
                diagnosticMessage = "Unknown COS operation failure",
                cause = this,
            )
    }

internal fun cosBackendFailure(
    operation: String,
    code: Int,
    message: String,
): CosStorageException =
    CosStorageException(
        kind = CosStorageFailureKind.BACKEND,
        errorCode = "API_$code",
        retryable = false,
        displayMessage = message.takeIf(String::isNotBlank),
        diagnosticMessage = "COS backend operation '$operation' failed (code=$code)",
    )

internal fun cosBackendException(
    operation: String,
    throwable: Throwable,
): CosStorageException {
    val mapped = throwable.toCosStorageException()
    return CosStorageException(
        kind = mapped.kind,
        errorCode = mapped.errorCode,
        retryable = mapped.retryable,
        displayMessage = mapped.displayMessage,
        diagnosticMessage = "COS backend operation '$operation' failed: ${mapped.message}",
        cause = throwable,
    )
}

internal fun ApiResult<String>.requirePrivateCosUrl(): String =
    when (this) {
        is ApiResult.Success ->
            data.trim().takeIf { it.isNotEmpty() }
                ?: throw CosStorageException(
                    kind = CosStorageFailureKind.INVALID_RESPONSE,
                    errorCode = "API_EMPTY_FILE_URL",
                    retryable = false,
                    diagnosticMessage = "COS private file URL response was empty",
                )

        is ApiResult.Failure ->
            throw cosBackendFailure(
                operation = "get_file_url",
                code = code,
                message = message,
            )

        is ApiResult.Exception ->
            throw cosBackendException(
                operation = "get_file_url",
                throwable = exception,
            )
    }

internal suspend fun <T> executeCosOperationWithRetry(
    clearCache: suspend () -> Unit,
    operation: suspend () -> T,
): T {
    return try {
        operation()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        val firstFailure = throwable.toCosStorageException()
        if (!firstFailure.retryable) {
            throw firstFailure
        }
        try {
            clearCache()
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (retryThrowable: Throwable) {
            throw retryThrowable.toCosStorageException()
        }
    }
}
