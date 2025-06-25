package com.ytone.longcare.domain.faceauth

import com.ytone.longcare.api.response.TencentAccessTokenResponse
import com.ytone.longcare.api.response.TencentApiTicketResponse
import com.ytone.longcare.common.network.ApiResult

/**
 * 腾讯人脸识别Repository接口
 */
interface TencentFaceRepository {
    
    /**
     * 获取access_token
     * 
     * @param appId 腾讯云应用ID
     * @param secret 腾讯云应用密钥
     * @return access_token结果
     */
    suspend fun getAccessToken(
        appId: String,
        secret: String
    ): ApiResult<TencentAccessTokenResponse>
    
    /**
     * 获取NONCE ticket
     * 
     * @param appId 业务流程唯一标识，即 WBappid
     * @param accessToken 访问令牌
     * @param userId 当前使用用户的唯一标识
     * @return api_ticket结果
     */
    suspend fun getApiTicket(
        appId: String,
        accessToken: String,
        userId: String
    ): ApiResult<TencentApiTicketResponse>
}