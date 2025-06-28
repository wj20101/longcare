package com.ytone.longcare.data.repository

import com.ytone.longcare.api.request.GetFaceIdRequest
import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.api.response.TencentAccessTokenResponse
import com.ytone.longcare.api.response.TencentApiTicketResponse
import com.ytone.longcare.api.response.TencentFaceIdResponse
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeTencentApiCall
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * 腾讯人脸识别Repository实现
 */
class TencentFaceRepositoryImpl @Inject constructor(
    private val apiService: TencentFaceApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : TencentFaceRepository {
    
    override suspend fun getAccessToken(
        appId: String,
        secret: String
    ): ApiResult<TencentAccessTokenResponse> = safeTencentApiCall(ioDispatcher, eventBus) {
        apiService.getAccessToken(
            appId = appId,
            secret = secret
        )
    }
    
    override suspend fun getApiTicket(
        appId: String,
        accessToken: String,
        userId: String
    ): ApiResult<TencentApiTicketResponse> = safeTencentApiCall(ioDispatcher, eventBus) {
        apiService.getApiTicket(
            appId = appId,
            accessToken = accessToken,
            userId = userId
        )
    }
    
    override suspend fun getSignTicket(
        appId: String,
        accessToken: String
    ): ApiResult<TencentApiTicketResponse> = safeTencentApiCall(ioDispatcher, eventBus) {
        apiService.getSignTicket(
            appId = appId,
            accessToken = accessToken
        )
    }
    
    override suspend fun getFaceId(
        appId: String,
        orderNo: String,
        name: String,
        idNo: String,
        userId: String,
        sign: String,
        nonce: String
    ): ApiResult<TencentFaceIdResponse> = safeTencentApiCall(ioDispatcher, eventBus) {
        val request = GetFaceIdRequest(
            appId = appId,
            orderNo = orderNo,
            name = name,
            idNo = idNo,
            userId = userId,
            sign = sign,
            nonce = nonce
        )
        apiService.getFaceId(
            request = request,
            orderNo = orderNo
        )
    }
}