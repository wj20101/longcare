package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 开始服务工单参数
 */
@Serializable
data class StartOrderParamModel(
    /**
     * 订单Id
     */
    @SerialName("orderid")
    val orderId: Long = 0L,

    /**
     * nfc设备号
     */
    @SerialName("nfc")
    val nfc: String = ""
)