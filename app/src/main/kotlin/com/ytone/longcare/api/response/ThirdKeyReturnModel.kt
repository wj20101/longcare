package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 第三方密钥返回模型
 * 用于解密SystemConfig中的thirdKeyStr字段
 */
@JsonClass(generateAdapter = true)
data class ThirdKeyReturnModel(
    /**
     * 腾讯云COS SecretId
     */
    @param:Json(name = "cosSecretId")
    val cosSecretId: String = "",

    /**
     * 腾讯云COS SecretKey
     */
    @param:Json(name = "cosSecretKey")
    val cosSecretKey: String = "",

    /**
     * 腾讯云人脸识别SecretId
     */
    @param:Json(name = "faceSecretId")
    val faceSecretId: String = "",

    /**
     * 腾讯云人脸识别SecretKey
     */
    @param:Json(name = "faceSecretKey")
    val faceSecretKey: String = "",

    /**
     * 高德地图Key
     */
    @param:Json(name = "amapKey")
    val amapKey: String = ""
)
