package com.ytone.longcare.common.utils

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FACE_AUTH_API_VERSION
import com.ytone.longcare.domain.faceauth.model.FACE_AUTH_SOURCE_PHOTO_TYPE_HD
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import kotlinx.coroutines.CancellationException

internal sealed interface FaceApiStepResult<out T> {
    data class Success<T>(val value: T) : FaceApiStepResult<T>
    data object Failure : FaceApiStepResult<Nothing>
}

internal suspend fun fetchFaceAccessToken(
    repository: TencentFaceRepository,
    config: FaceVerificationConfig
): FaceApiStepResult<String> {
    return runFaceApiCall {
        val result = repository.getAccessToken(config.appId, config.secret)
        result.requiredValue { response ->
            response.accessToken?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}

internal suspend fun fetchFaceSignTicket(
    repository: TencentFaceRepository,
    config: FaceVerificationConfig,
    accessToken: String
): FaceApiStepResult<String> {
    return runFaceApiCall {
        val result = repository.getSignTicket(config.appId, accessToken)
        result.requiredValue { response ->
            response.tickets?.firstOrNull { it.value.isNotBlank() }?.value
        }
    }
}

internal suspend fun fetchFaceNonceTicket(
    repository: TencentFaceRepository,
    config: FaceVerificationConfig,
    accessToken: String,
    userId: String
): FaceApiStepResult<String> {
    return runFaceApiCall {
        val result = repository.getApiTicket(config.appId, accessToken, userId)
        result.requiredValue { response ->
            response.tickets?.firstOrNull { it.value.isNotBlank() }?.value
        }
    }
}

internal suspend fun fetchFaceId(
    repository: TencentFaceRepository,
    config: FaceVerificationConfig,
    request: FaceVerificationRequest,
    signTicket: String,
    nonce: String
): FaceApiStepResult<String> {
    return runFaceApiCall {
        val sign = buildFaceVerifySign(
            appId = config.appId,
            nonce = nonce,
            apiTicket = signTicket,
            userId = request.userId
        )
        val result = repository.getFaceId(
            appId = config.appId,
            orderNo = request.orderNo,
            name = if (request.sourcePhotoStr != null) null else request.name,
            idNo = if (request.sourcePhotoStr != null) null else request.idNo,
            userId = request.userId,
            sign = sign,
            nonce = nonce,
            sourcePhotoStr = request.sourcePhotoStr,
            sourcePhotoType = if (request.sourcePhotoStr != null) {
                FACE_AUTH_SOURCE_PHOTO_TYPE_HD
            } else {
                null
            }
        )
        result.requiredValue { response ->
            response.result?.faceId?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}

internal fun buildFaceNonce(length: Int = 32): String {
    return RandomUtils.generateRandomString(length)
}

internal fun buildFaceVerifySign(
    appId: String,
    nonce: String,
    userId: String,
    apiTicket: String
): String {
    val version = FACE_AUTH_API_VERSION
    val params = listOf(version, appId, apiTicket, nonce, userId).sorted()
    val signString = params.joinToString("")
    return signString.sha1Hex().uppercase()
}

private fun String.sha1Hex(): String {
    return try {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val result = digest.digest(toByteArray())
        result.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        ""
    }
}

private inline fun <T, R> ApiResult<T>.requiredValue(
    valueProvider: (T) -> R?
): FaceApiStepResult<R> {
    return when (this) {
        is ApiResult.Success -> {
            valueProvider(data)
                ?.let { FaceApiStepResult.Success(it) }
                ?: FaceApiStepResult.Failure
        }

        is ApiResult.Failure -> FaceApiStepResult.Failure
        is ApiResult.Exception -> FaceApiStepResult.Failure
    }
}

private suspend inline fun <T> runFaceApiCall(
    block: suspend () -> FaceApiStepResult<T>
): FaceApiStepResult<T> {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        FaceApiStepResult.Failure
    }
}
