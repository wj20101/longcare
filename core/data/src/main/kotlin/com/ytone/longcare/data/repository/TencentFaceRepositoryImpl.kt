package com.ytone.longcare.data.repository

import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.model.GetFaceIdRequest
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import com.ytone.longcare.model.TencentFaceIdResponse
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import javax.inject.Inject

/**
 * 腾讯人脸识别Repository实现
 */
class TencentFaceRepositoryImpl @Inject constructor(
    private val apiService: TencentFaceApiService,
    private val configManager: SystemConfigManager,
    private val faceSession: UserSessionTracker,
) : TencentFaceRepository {
    private val credentialCache get() = faceSession.credentials

    override suspend fun getAccessToken(
        config: FaceVerificationConfig,
    ): ApiResult<TencentAccessTokenResponse> =
        credentialCache.getAccessToken(config.appId, config.sessionGeneration, { faceSession.isCurrent(config.sessionGeneration) }) {
            val secret = configManager.loadFaceSecret(config)
                ?: return@getAccessToken ApiResult.Failure(code = -1, message = "Face configuration unavailable")
            faceSession.requireCurrent(config.sessionGeneration)
            apiService.getAccessToken(
                appId = config.appId,
                secret = secret
            )
        }

    override suspend fun getApiTicket(
        appId: String,
        accessToken: String,
        userId: String,
        sessionGeneration: Long,
    ): ApiResult<TencentApiTicketResponse> =
        inSession(sessionGeneration) {
            apiService.getApiTicket(appId = appId, accessToken = accessToken, userId = userId)
        }

    override suspend fun getSignTicket(
        appId: String,
        accessToken: String,
        sessionGeneration: Long,
    ): ApiResult<TencentApiTicketResponse> =
        credentialCache.getSignTicket(appId, sessionGeneration, { faceSession.isCurrent(sessionGeneration) }) {
            apiService.getSignTicket(
                appId = appId,
                accessToken = accessToken
            )
        }

    override suspend fun getFaceId(
        appId: String,
        orderNo: String,
        name: String?,
        idNo: String?,
        userId: String,
        sign: String,
        nonce: String,
        sourcePhotoStr: String?,
        sourcePhotoType: String?,
        sessionGeneration: Long,
    ): ApiResult<TencentFaceIdResponse> {
        val request = GetFaceIdRequest(
            appId = appId,
            orderNo = orderNo,
            name = name,
            idNo = idNo,
            userId = userId,
            sign = sign,
            nonce = nonce,
            sourcePhotoStr = sourcePhotoStr,
            sourcePhotoType = sourcePhotoType
        )
        return inSession(sessionGeneration) {
            apiService.getFaceId(request = request, orderNo = orderNo)
        }
    }

    private suspend fun <T> inSession(generation: Long, block: suspend () -> T): T {
        faceSession.requireCurrent(generation)
        val result = block()
        faceSession.requireCurrent(generation)
        return result
    }
}
