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
     * 高德地图API Key
     */
    @param:Json(name = "GaoDeMapApiKey")
    val gaoDeMapApiKey: String = "",

    /**
     * 腾讯云人脸识别AppId
     */
    @param:Json(name = "TxFaceAppId")
    val txFaceAppId: String = "",

    /**
     * 腾讯云人脸识别AppSecret
     */
    @param:Json(name = "TxFaceAppSecret")
    val txFaceAppSecret: String = "",

    /**
     * 腾讯云人脸识别AppLicence
     */
    @param:Json(name = "TxFaceAppLicence")
    val txFaceAppLicence: String = ""
)
