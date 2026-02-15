package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 启动前的配置信息
 */
@JsonClass(generateAdapter = true)
data class StartConfigResultModel(
    /**
     * 用户协议地址
     */
    @param:Json(name = "userXieYiUrl")
    val userXieYiUrl: String = "",

    /**
     * 隐私协议地址
     */
    @param:Json(name = "yinSiXieYiUrl")
    val yinSiXieYiUrl: String = ""
)
