package com.ytone.longcare.features.identification.data

import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.CheckFaceGateway
import com.ytone.longcare.features.identification.domain.CheckFaceRemoteResult
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.result.SessionInvalidationCode
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
            is ApiResult.Failure -> {
                if (SessionInvalidationCode.requiresLogout(result.code)) {
                    CheckFaceRemoteResult.SessionInvalidated
                } else {
                    resolveFailedVerification(
                        fallback = CheckFaceRemoteResult.Rejected(
                            code = result.code,
                            message = result.message.takeIf(String::isNotBlank),
                        ),
                    )
                }
            }

            is ApiResult.Exception -> resolveFailedVerification(
                fallback = CheckFaceRemoteResult.NetworkError,
            )
        }
    }

    /**
     * The CheckFace contract does not identify whether a failure means that the current account
     * has no registered face. Re-querying GetFace gives the UI an explicit, stable decision and
     * avoids inferring business state from mutable server messages.
     */
    private suspend fun resolveFailedVerification(
        fallback: CheckFaceRemoteResult,
    ): CheckFaceRemoteResult = when (val faceResult = identificationRepository.getFace()) {
        is ApiResult.Success -> {
            if (faceResult.data.faceImgUrl.isBlank()) {
                CheckFaceRemoteResult.MissingRegisteredFace
            } else {
                fallback
            }
        }

        is ApiResult.Failure -> {
            if (SessionInvalidationCode.requiresLogout(faceResult.code)) {
                CheckFaceRemoteResult.SessionInvalidated
            } else {
                CheckFaceRemoteResult.MissingRegisteredFace
            }
        }

        is ApiResult.Exception -> CheckFaceRemoteResult.MissingRegisteredFace
    }
}
