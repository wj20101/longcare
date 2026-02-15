package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 腾讯云API响应基类
 * 包含所有腾讯云API响应的通用字段
 */
interface TencentApiResponse {
    /**
     * 响应码：0：成功 非0：失败
     */
    val code: String
    
    /**
     * 请求结果描述
     */
    val msg: String
    
    /**
     * 调用接口的时间
     */
    val transactionTime: String
}

/**
 * 腾讯云获取access_token响应
 * 根据官方文档：https://cloud.tencent.com/document/product/1007/37304
 */
@JsonClass(generateAdapter = true)
data class TencentAccessTokenResponse(
    /**
     * 响应码：0：成功 非0：失败
     */
    @param:Json(name = "code")
    override val code: String,
    
    /**
     * 请求结果描述
     */
    @param:Json(name = "msg")
    override val msg: String,
    
    /**
     * 调用接口的时间
     */
    @param:Json(name = "transactionTime")
    override val transactionTime: String,
    
    /**
     * access_token 的值
     */
    @param:Json(name = "access_token")
    val accessToken: String? = null,
    
    /**
     * access_token 失效的绝对时间
     */
    @param:Json(name = "expire_time")
    val expireTime: String? = null,
    
    /**
     * access_token 的最大生存时间（单位：秒）
     */
    @param:Json(name = "expire_in")
    val expireIn: Int? = null
) : TencentApiResponse

/**
 * 腾讯云获取NONCE ticket响应
 * 根据官方文档：https://cloud.tencent.com/document/product/1007/37306
 */
@JsonClass(generateAdapter = true)
data class TencentApiTicketResponse(
    /**
     * 响应码：0：成功 非0：失败
     */
    @param:Json(name = "code")
    override val code: String,
    
    /**
     * 请求结果描述
     */
    @param:Json(name = "msg")
    override val msg: String,
    
    /**
     * 调用接口的时间
     */
    @param:Json(name = "transactionTime")
    override val transactionTime: String,
    
    /**
     * ticket 返回数组
     */
    @param:Json(name = "tickets")
    val tickets: List<TicketInfo>? = null
) : TencentApiResponse

/**
 * Ticket信息
 */
@JsonClass(generateAdapter = true)
data class TicketInfo(
    /**
     * ticket 的值
     */
    @param:Json(name = "value")
    val value: String,
    
    /**
     * ticket 失效的绝对时间
     */
    @param:Json(name = "expire_time")
    val expireTime: String,
    
    /**
     * ticket 的最大生存时间（单位：秒）
     */
    @param:Json(name = "expire_in")
    val expireIn: String
)

/**
 * 腾讯云获取faceId响应
 * 根据官方文档：https://cloud.tencent.com/document/product/1007/35866
 */
@JsonClass(generateAdapter = true)
data class TencentFaceIdResponse(
    /**
     * 响应码：0：成功 非0：失败
     */
    @param:Json(name = "code")
    override val code: String,
    
    /**
     * 请求结果描述
     */
    @param:Json(name = "msg")
    override val msg: String,
    
    /**
     * 调用接口的时间
     */
    @param:Json(name = "transactionTime")
    override val transactionTime: String,
    
    /**
     * 请求业务流水号
     */
    @param:Json(name = "bizSeqNo")
    val bizSeqNo: String? = null,
    
    /**
     * 订单编号
     */
    @param:Json(name = "orderNo")
    val orderNo: String? = null,
    
    /**
     * 结果信息
     */
    @param:Json(name = "result")
    val result: FaceIdResult? = null
) : TencentApiResponse

/**
 * FaceId结果信息
 */
@JsonClass(generateAdapter = true)
data class FaceIdResult(
    /**
     * 业务流水号
     */
    @param:Json(name = "bizSeqNo")
    val bizSeqNo: String? = null,
    
    /**
     * 调用接口的时间
     */
    @param:Json(name = "transactionTime")
    val transactionTime: String? = null,
    
    /**
     * 合作方订单号
     */
    @param:Json(name = "orderNo")
    val orderNo: String? = null,
    
    /**
     * 此次刷脸用户标识
     */
    @param:Json(name = "faceId")
    val faceId: String? = null,
    
    /**
     * 是否成功
     */
    @param:Json(name = "success")
    val success: Boolean? = null
)