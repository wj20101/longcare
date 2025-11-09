package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 系统相关配置
 */
@JsonClass(generateAdapter = true)
data class SystemConfigModel(
    /**
     * 公司名称
     */
    @param:Json(name = "companyName")
    val companyName: String = "",

    /**
     * 上传最大的数量
     */
    @param:Json(name = "maxImgNum")
    val maxImgNum: Int = 0,

    /**
     * 水印logo图片
     */
    @param:Json(name = "syLogoImg")
    val syLogoImg: String = "",

    /**
     * 选择服务的类型0:默认不全选可改变 1:默认全选不能改变
     */
    @param:Json(name = "selectServiceType")
    val selectServiceType: Int = 0,

    /**
     * 第三方密钥字符串（AES加密后的ThirdKeyReturnModel JSON）
     * 解密规则: AES解密(thirdKeyStr, 请求Config接口头部的AESKEY)
     */
    @param:Json(name = "thirdKeyStr")
    val thirdKeyStr: String = ""
)