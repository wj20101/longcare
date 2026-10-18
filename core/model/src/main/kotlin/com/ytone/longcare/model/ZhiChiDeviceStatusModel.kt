package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** 首页咫尺设备状态；状态文字、颜色和 H5 地址均由服务端提供。 */
@JsonClass(generateAdapter = true)
data class ZhiChiDeviceStatusModel(
    @param:Json(name = "deviceSn") val deviceSn: String = "",
    @param:Json(name = "statusColor") val statusColor: String = "",
    @param:Json(name = "status") val status: String = "",
    @param:Json(name = "installUrl") val installUrl: String = "",
    @param:Json(name = "previewUrl") val previewUrl: String = "",
)
