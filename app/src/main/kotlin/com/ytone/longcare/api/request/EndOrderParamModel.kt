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
    @param:Json(name = "orderid")
    val orderId: Long = 0L,

    /**
     * nfc设备号
     */
    @param:Json(name = "nfc")
    val nfc: String = "",

    /**
     * 经度
     */
    @param:Json(name = "longitude")
    val longitude: String = "",

    /**
     * 纬度
     */
    @param:Json(name = "latitude")
    val latitude: String = "",

    /**
     * 服务项目ID集合
     */
    @param:Json(name = "porjectIdList")
    val porjectIdList: List<Int> = emptyList(),

    /**
     * 开始图片集合
     */
    @param:Json(name = "beginImgList")
    val beginImgList: List<String> = emptyList(),

    /**
     * 护理中图片集合
     */
    @param:Json(name = "centerImgList")
    val centerImgList: List<String> = emptyList(),
    
    /**
     * 护理后图片集合
     */
    @param:Json(name = "endImgList")
    val endImgList: List<String> = emptyList(),
    /**
     * 结束类型 1正常 2提前
     */
    @param:Json(name = "endType")
    val endType: Int = 1
)