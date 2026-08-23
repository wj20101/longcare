package com.ytone.longcare.features.identification.domain

import javax.inject.Inject

sealed interface CheckFaceResult {
    data object Success : CheckFaceResult

    data class Error(val failure: CheckFaceFailure) : CheckFaceResult
}

sealed interface CheckFaceFailure {
    data object UnsupportedOrder : CheckFaceFailure

    data object MissingImage : CheckFaceFailure

    data class Rejected(val serverMessage: String?) : CheckFaceFailure

    data object NetworkError : CheckFaceFailure
}

/** `/V1/User/CheckFace` documents orderId as a signed 32-bit integer. */
object CheckFaceOrderIdPolicy {
    fun isSupported(orderId: Long): Boolean = orderId in 1L..Int.MAX_VALUE.toLong()
}

class CheckFaceUseCase @Inject constructor(
    private val gateway: CheckFaceGateway,
) {
    suspend fun execute(
        orderId: Long,
        faceImageBase64: String,
    ): CheckFaceResult {
        if (!CheckFaceOrderIdPolicy.isSupported(orderId)) {
            return CheckFaceResult.Error(CheckFaceFailure.UnsupportedOrder)
        }
        if (faceImageBase64.isBlank()) {
            return CheckFaceResult.Error(CheckFaceFailure.MissingImage)
        }

        return when (
            val result = gateway.checkFace(
                orderId = orderId.toInt(),
                faceImageBase64 = faceImageBase64,
            )
        ) {
            CheckFaceRemoteResult.Success -> CheckFaceResult.Success
            is CheckFaceRemoteResult.Rejected -> CheckFaceResult.Error(
                CheckFaceFailure.Rejected(result.message),
            )
            CheckFaceRemoteResult.NetworkError -> CheckFaceResult.Error(
                CheckFaceFailure.NetworkError,
            )
        }
    }
}
