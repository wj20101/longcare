package com.ytone.longcare.features.identification.data

import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.CheckFaceGateway
import com.ytone.longcare.features.identification.domain.CheckFaceRemoteResult
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.result.SessionInvalidationCode
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull

private const val FACE_COMPARISON_TIMEOUT_MS = 20_000L

class CheckFaceGatewayImpl @Inject constructor(
    private val identificationRepository: IdentificationRepository,
) : CheckFaceGateway {
    override suspend fun checkFace(
        orderId: Int,
        faceImageBase64: String,
    ): CheckFaceRemoteResult {
        val result = withTimeoutOrNull(FACE_COMPARISON_TIMEOUT_MS) {
            identificationRepository.checkFace(
                CheckFaceParamModel(
                    orderId = orderId,
                    faceImg = faceImageBase64,
                ),
            )
        } ?: return CheckFaceRemoteResult.NetworkError

        return when (result) {
            is ApiResult.Success -> CheckFaceRemoteResult.Success
            is ApiResult.Failure -> {
                if (SessionInvalidationCode.requiresLogout(result.code)) {
                    CheckFaceRemoteResult.SessionInvalidated
                } else {
                    CheckFaceRemoteResult.Rejected(
                        code = result.code,
                        message = result.message.takeIf(String::isNotBlank),
                    )
                }
            }

            is ApiResult.Exception -> CheckFaceRemoteResult.NetworkError
        }
    }
}
