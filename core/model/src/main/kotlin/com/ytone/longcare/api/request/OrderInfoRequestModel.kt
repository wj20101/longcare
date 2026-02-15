package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 获取订单详情请求参数模型
 * @param orderId 订单ID
 * @param planId 计划ID，默认值为0
 */
@JsonClass(generateAdapter = true)
@Serializable
@Parcelize
data class OrderInfoRequestModel(
    @param:Json(name = "orderId")
    val orderId: Long,

    @param:Json(name = "planId")
    val planId: Int
) : Parcelable