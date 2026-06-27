package com.ytone.longcare.common.utils

import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FACE_AUTH_API_VERSION
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest

internal data class FaceVerifyParams(
    val faceId: String,
    val orderNo: String,
    val appId: String,
    val version: String = FACE_AUTH_API_VERSION,
    val nonce: String,
    val userId: String,
    val sign: String,
    val keyLicence: String
)

internal sealed interface FaceVerifyParamBuildResult {
    data class Success(val params: FaceVerifyParams) : FaceVerifyParamBuildResult
    data class Failure(val message: String) : FaceVerifyParamBuildResult
}

internal class FaceVerificationParamAssembler(
    private val tencentFaceRepository: TencentFaceRepository
) {

    suspend fun build(
        config: FaceVerificationConfig,
        request: FaceVerificationRequest
    ): FaceVerifyParamBuildResult {
        val nonce = buildFaceNonce()

        val accessTokenResult = fetchFaceAccessToken(
            repository = tencentFaceRepository,
            config = config,
        )
        val accessToken = when (accessTokenResult) {
            is FaceApiStepResult.Success -> accessTokenResult.value
            is FaceApiStepResult.Failure -> return FaceVerifyParamBuildResult.Failure(accessTokenResult.message)
        }

        val signTicketResult = fetchFaceSignTicket(
            repository = tencentFaceRepository,
            config = config,
            accessToken = accessToken,
        )
        val signTicket = when (signTicketResult) {
            is FaceApiStepResult.Success -> signTicketResult.value
            is FaceApiStepResult.Failure -> return FaceVerifyParamBuildResult.Failure(signTicketResult.message)
        }

        val faceIdResult = fetchFaceId(
            repository = tencentFaceRepository,
            config = config,
            request = request,
            signTicket = signTicket,
            nonce = nonce,
        )
        val faceId = when (faceIdResult) {
            is FaceApiStepResult.Success -> faceIdResult.value
            is FaceApiStepResult.Failure -> return FaceVerifyParamBuildResult.Failure(faceIdResult.message)
        }

        val nonceTicketResult = fetchFaceNonceTicket(
            repository = tencentFaceRepository,
            config = config,
            accessToken = accessToken,
            userId = request.userId,
        )
        val nonceTicket = when (nonceTicketResult) {
            is FaceApiStepResult.Success -> nonceTicketResult.value
            is FaceApiStepResult.Failure -> return FaceVerifyParamBuildResult.Failure(nonceTicketResult.message)
        }

        val params = createFaceVerifyParams(config, request, faceId, nonceTicket, nonce)
        return FaceVerifyParamBuildResult.Success(params)
    }

    private fun createFaceVerifyParams(
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        faceId: String,
        nonceTicket: String,
        nonce: String
    ): FaceVerifyParams {
        val sign = buildFaceVerifySign(
            appId = config.appId,
            nonce = nonce,
            apiTicket = nonceTicket,
            userId = request.userId
        )

        return FaceVerifyParams(
            faceId = faceId,
            orderNo = request.orderNo,
            appId = config.appId,
            nonce = nonce,
            userId = request.userId,
            sign = sign,
            keyLicence = config.licence
        )
    }
}
