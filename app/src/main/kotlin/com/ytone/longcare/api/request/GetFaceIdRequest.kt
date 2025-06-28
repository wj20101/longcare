package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.ytone.longcare.common.utils.FaceVerificationManager.Companion.FACE_VERSION

/**
 * 获取faceId的请求体
 * 根据腾讯云官方文档：https://cloud.tencent.com/document/product/1007/35866
 */
@JsonClass(generateAdapter = true)
data class GetFaceIdRequest(
    /**
     * 业务流程唯一标识，即 WBappid
     */
    @param:Json(name = "appId")
    val appId: String,
    
    /**
     * 订单号，字母/数字组成的字符串，由合作方上传，每次唯一，不能超过32位
     */
    @param:Json(name = "orderNo")
    val orderNo: String,
    
    /**
     * 姓名
     */
    @param:Json(name = "name")
    val name: String,
    
    /**
     * 证件号码
     */
    @param:Json(name = "idNo")
    val idNo: String,
    
    /**
     * 用户ID，用户的唯一标识（不能带有特殊字符），需要跟生成签名的userId保持一致
     */
    @param:Json(name = "userId")
    val userId: String,
    
    /**
     * 版本号，默认参数值为：1.0.0
     */
    @param:Json(name = "version")
    val version: String = FACE_VERSION,
    
    /**
     * 签名：使用上面生成的签名
     */
    @param:Json(name = "sign")
    val sign: String,
    
    /**
     * 随机数
     */
    @param:Json(name = "nonce")
    val nonce: String
)