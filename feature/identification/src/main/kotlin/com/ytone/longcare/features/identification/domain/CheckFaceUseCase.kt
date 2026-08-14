package com.ytone.longcare.features.identification.domain

import javax.inject.Inject

sealed interface CheckFaceResult {
    data object Success : CheckFaceResult

    data class Error(val message: String) : CheckFaceResult
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
            return CheckFaceResult.Error("订单信息异常，请返回后重试")
        }
        if (faceImageBase64.isBlank()) {
            return CheckFaceResult.Error("未获取到有效人脸照片，请重新拍摄")
        }

        return when (
            val result = gateway.checkFace(
                orderId = orderId.toInt(),
                faceImageBase64 = faceImageBase64,
            )
        ) {
            CheckFaceRemoteResult.Success -> CheckFaceResult.Success
            is CheckFaceRemoteResult.Error -> CheckFaceResult.Error(result.message)
        }
    }
}
