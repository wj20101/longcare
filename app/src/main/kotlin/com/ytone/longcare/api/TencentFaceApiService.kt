package com.ytone.longcare.api

import com.ytone.longcare.api.response.TencentAccessTokenResponse
import com.ytone.longcare.api.response.TencentApiTicketResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 腾讯云人脸识别API服务接口
 */
interface TencentFaceApiService {
    
    /**
     * 获取access_token
     * 根据腾讯云官方文档：https://cloud.tencent.com/document/product/1007/37304
     * 
     * @param appId 业务流程唯一标识，即 WBappid
     * @param secret WBappid 对应的密钥
     * @param grantType 授权类型，默认值为：client_credential（必须小写）
     * @param version 版本号，默认值为：1.0.0
     * @return access_token响应
     */
    @GET("/api/oauth2/access_token")
    suspend fun getAccessToken(
        @Query("appId") appId: String,
        @Query("secret") secret: String,
        @Query("grant_type") grantType: String = "client_credential",
        @Query("version") version: String = "1.0.0"
    ): TencentAccessTokenResponse
    
    /**
     * 获取NONCE ticket
     * 根据腾讯云官方文档：https://cloud.tencent.com/document/product/1007/37306
     * 
     * @param appId 业务流程唯一标识，即 WBappid
     * @param accessToken 根据 Access Token 获取指引进行获取
     * @param type ticket 类型，默认值：NONCE（必须大写）
     * @param version 版本号
     * @param userId 当前使用用户的唯一标识，需合作伙伴自行定义
     * @return api_ticket响应
     */
    @GET("/api/oauth2/api_ticket")
    suspend fun getApiTicket(
        @Query("appId") appId: String,
        @Query("access_token") accessToken: String,
        @Query("type") type: String = "NONCE",
        @Query("version") version: String = "1.0.0",
        @Query("user_id") userId: String
    ): TencentApiTicketResponse
}