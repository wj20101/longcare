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