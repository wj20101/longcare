package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 结束服务工单参数
 */
@JsonClass(generateAdapter = true)
data class EndOrderParamModel(
    /**
     * 订单Id
     */
    @field:Json("orderid")
    val orderId: Long = 0L,

    /**
     * nfc设备号
     */
    @field:Json("nfc")
    val nfc: String = "",

    /**
     * 完成的服务项目Id集合
     */
    @field:Json("porjectIdList")
    val projectIdList: List<Int> = emptyList(),

    /**
     * 开始图片集合
     */
    @field:Json("beginImgList")
    val beginImgList: List<String> = emptyList(),

    /**
     * 结束图片集合
     */
    @field:Json("endImgList")
    val endImgList: List<String> = emptyList()
)