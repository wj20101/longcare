package com.ytone.longcare.domain.faceauth

import com.ytone.longcare.api.response.TencentAccessTokenResponse
import com.ytone.longcare.api.response.TencentApiTicketResponse
import com.ytone.longcare.api.response.TencentFaceIdResponse
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

    /**
     * 获取SIGN ticket
     * 用于获取faceId时的签名生成
     *
     * @param appId 业务流程唯一标识，即 WBappid
     * @param accessToken 访问令牌
     * @return api_ticket结果
     */
    suspend fun getSignTicket(
        appId: String,
        accessToken: String
    ): ApiResult<TencentApiTicketResponse>

    /**
     * 获取faceId
     * 合作方后台上传身份信息，获取faceId
     *
     * @param appId 业务流程唯一标识，即 WBappid
     * @param orderNo 订单号
     * @param name 姓名
     * @param idNo 证件号码
     * @param userId 用户ID
     * @param sign 签名
     * @param nonce 随机数
     * @param sourcePhotoStr 比对源照片
     * @param sourcePhotoType 比对源照片类型
     * @return faceId结果
     */
    suspend fun getFaceId(
        appId: String,
        orderNo: String,
        name: String?,
        idNo: String?,
        userId: String,
        sign: String,
        nonce: String,
        sourcePhotoStr: String?,
        sourcePhotoType: String?
    ): ApiResult<TencentFaceIdResponse>
}
