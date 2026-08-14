package com.ytone.longcare.features.identification.data

import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.CheckFaceGateway
import com.ytone.longcare.features.identification.domain.CheckFaceRemoteResult
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.result.ApiResult
import javax.inject.Inject

class CheckFaceGatewayImpl @Inject constructor(
    private val identificationRepository: IdentificationRepository,
) : CheckFaceGateway {
    override suspend fun checkFace(
        orderId: Int,
        faceImageBase64: String,
    ): CheckFaceRemoteResult {
        val result = identificationRepository.checkFace(
            CheckFaceParamModel(
                orderId = orderId,
                faceImg = faceImageBase64,
            ),
        )

        return when (result) {
            is ApiResult.Success -> CheckFaceRemoteResult.Success
            is ApiResult.Failure -> CheckFaceRemoteResult.Error(
                result.message.ifBlank { "人脸验证未通过，请重新拍摄" },
            )
            is ApiResult.Exception -> CheckFaceRemoteResult.Error(
                result.exception.message ?: "网络连接异常，请稍后重试",
            )
        }
    }
}
