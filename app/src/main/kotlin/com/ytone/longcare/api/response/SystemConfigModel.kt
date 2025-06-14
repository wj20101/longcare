package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 系统相关配置
 */
@JsonClass(generateAdapter = true)
data class SystemConfigModel(
    /**
     * 腾讯地图key
     */
    @Json(name = "TencentMapKey")
    val tencentMapKey: String = "",

    /**
     * 腾讯地图围栏半径
     */
    @Json(name = "TencentMapFenceRadius")
    val tencentMapFenceRadius: Int = 0,

    /**
     * 腾讯地图定位上传间隔时间（秒）
     */
    @Json(name = "TencentMapUploadInterval")
    val tencentMapUploadInterval: Int = 0,

    /**
     * 腾讯地图定位上传距离（米）
     */
    @Json(name = "TencentMapUploadDistance")
    val tencentMapUploadDistance: Int = 0
)