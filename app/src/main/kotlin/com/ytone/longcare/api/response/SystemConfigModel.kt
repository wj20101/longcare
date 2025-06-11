package com.ytone.longcare.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 系统相关配置
 */
@Serializable
data class SystemConfigModel(
    /**
     * 腾讯地图key
     */
    @SerialName("TencentMapKey")
    val tencentMapKey: String = "",

    /**
     * 腾讯地图围栏半径
     */
    @SerialName("TencentMapFenceRadius")
    val tencentMapFenceRadius: Int = 0,

    /**
     * 腾讯地图定位上传间隔时间（秒）
     */
    @SerialName("TencentMapUploadInterval")
    val tencentMapUploadInterval: Int = 0,

    /**
     * 腾讯地图定位上传距离（米）
     */
    @SerialName("TencentMapUploadDistance")
    val tencentMapUploadDistance: Int = 0
)