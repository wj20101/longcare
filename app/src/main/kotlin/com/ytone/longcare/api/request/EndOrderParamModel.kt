package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 结束服务工单参数
 */
@Serializable
data class EndOrderParamModel(
    /**
     * 订单Id
     */
    @SerialName("orderid")
    val orderId: Long = 0L,

    /**
     * nfc设备号
     */
    @SerialName("nfc")
    val nfc: String = "",

    /**
     * 完成的服务项目Id集合
     */
    @SerialName("porjectIdList")
    val projectIdList: List<Int> = emptyList(),

    /**
     * 开始图片集合
     */
    @SerialName("beginImgList")
    val beginImgList: List<String> = emptyList(),

    /**
     * 结束图片集合
     */
    @SerialName("endImgList")
    val endImgList: List<String> = emptyList()
)