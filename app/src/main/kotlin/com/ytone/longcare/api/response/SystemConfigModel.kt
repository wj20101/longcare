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
    @Json(name = "companyName")
    val companyName: String = "",

    /**
     * 上传最大的数量
     */
    @Json(name = "maxImgNum")
    val maxImgNum: Int = 0
)