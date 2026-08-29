package com.ytone.longcare.data.repository

import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.model.GetFaceIdRequest
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import com.ytone.longcare.model.TencentFaceIdResponse
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * 腾讯人脸识别Repository实现
 */
class TencentFaceRepositoryImpl @Inject constructor(
    private val apiService: TencentFaceApiService,
    private val sessionSecretProvider: SessionSecretProvider,
) : TencentFaceRepository, SessionRuntimeCleanupHook {
    private val credentialCache = TencentCredentialCache()

    override suspend fun getAccessToken(
        appId: String,
        secret: String
    ): ApiResult<TencentAccessTokenResponse> {
        val session = requireSessionFingerprint()
        return credentialCache.getAccessToken(session, appId) {
            apiService.getAccessToken(
                appId = appId,
                secret = secret
            )
        }.also { requireCurrentSession(session) }
    }

    override suspend fun getApiTicket(
        appId: String,
        accessToken: String,
        userId: String
    ): ApiResult<TencentApiTicketResponse> {
        val session = requireSessionFingerprint()
        return apiService.getApiTicket(
            appId = appId,
            accessToken = accessToken,
            userId = userId
        ).also { requireCurrentSession(session) }
    }

    override suspend fun getSignTicket(
        appId: String,
        accessToken: String
    ): ApiResult<TencentApiTicketResponse> {
        val session = requireSessionFingerprint()
        return credentialCache.getSignTicket(session, appId) {
            apiService.getSignTicket(
                appId = appId,
                accessToken = accessToken
            )
        }.also { requireCurrentSession(session) }
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
        val session = requireSessionFingerprint()
        return apiService.getFaceId(
            request = request,
            orderNo = orderNo
        ).also { requireCurrentSession(session) }
    }

    override suspend fun cleanup(identity: SessionRuntimeIdentity) {
        credentialCache.clear()
    }

    private fun requireSessionFingerprint(): String =
        sessionSecretProvider.activeSessionFingerprint()
            ?: throw IllegalStateException("Tencent face operation requires an active session")

    private fun requireCurrentSession(expected: String) {
        if (sessionSecretProvider.activeSessionFingerprint() != expected) {
            throw CancellationException("Tencent credential belongs to an expired session")
        }
    }
}
