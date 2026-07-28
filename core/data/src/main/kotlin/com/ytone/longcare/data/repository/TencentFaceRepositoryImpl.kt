package com.ytone.longcare.data.repository

import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.model.GetFaceIdRequest
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import com.ytone.longcare.model.TencentFaceIdResponse
import javax.inject.Inject

/**
 * 腾讯人脸识别Repository实现
 */
class TencentFaceRepositoryImpl @Inject constructor(
    private val apiService: TencentFaceApiService,
) : TencentFaceRepository {
    private val credentialCache = TencentCredentialCache()

    override suspend fun getAccessToken(
        appId: String,
        secret: String
    ): ApiResult<TencentAccessTokenResponse> =
        credentialCache.getAccessToken(appId) {
            apiService.getAccessToken(
                appId = appId,
                secret = secret
            )
        }

    override suspend fun getApiTicket(
        appId: String,
        accessToken: String,
        userId: String
    ): ApiResult<TencentApiTicketResponse> =
        apiService.getApiTicket(
            appId = appId,
            accessToken = accessToken,
            userId = userId
        )

    override suspend fun getSignTicket(
        appId: String,
        accessToken: String
    ): ApiResult<TencentApiTicketResponse> =
        credentialCache.getSignTicket(appId) {
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
        sourcePhotoType: String?
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
        return apiService.getFaceId(
            request = request,
            orderNo = orderNo
        )
    }
}
