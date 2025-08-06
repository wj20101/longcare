package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * App版本信息
 */
@JsonClass(generateAdapter = true)
data class AppVersionModel(
    /**
     * 版本号
     */
    @param:Json(name = "versionCode")
    val versionCode: Int = 0,

    /**
     * 版本名
     */
    @param:Json(name = "versionName")
    val versionName: String = "",

    /**
     * 更新类型 1普通 2强制
     */
    @param:Json(name = "upType")
    val upType: Int = 1,

    /**
     * 更新说明
     */
    @param:Json(name = "remarks")
    val remarks: String = "",

    /**
     * 更新平台ios或android
     */
    @param:Json(name = "platform")
    val platform: String = "",

    /**
     * 下载地址
     */
    @param:Json(name = "downUrl")
    val downUrl: String = ""
)