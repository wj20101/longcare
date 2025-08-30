package com.ytone.longcare.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.logE
import kotlinx.serialization.json.Json

/**
 * 自定义 NavType 用于处理 OrderInfoRequestModel 类型的序列化和反序列化
 */
object OrderInfoRequestModelNavType : NavType<OrderInfoRequestModel>(isNullableAllowed = false) {

    override fun get(bundle: Bundle, key: String): OrderInfoRequestModel? {
        return bundle.getString(key)?.let { jsonString ->
            try {
                Json.decodeFromString<OrderInfoRequestModel>(Uri.decode(jsonString))
            } catch (e: Exception) {
                logE(message = "OrderInfoRequestModelNavType get fail", throwable = e)
                null
            }
        }
    }

    override fun parseValue(value: String): OrderInfoRequestModel {
        return try {
            Json.decodeFromString<OrderInfoRequestModel>(Uri.decode(value))
        } catch (e: Exception) {
            logE(message = "OrderInfoRequestModelNavType parseValue fail", throwable = e)
            throw IllegalArgumentException("Failed to parse OrderInfoRequestModel from value: $value", e)
        }
    }

    override fun put(bundle: Bundle, key: String, value: OrderInfoRequestModel) {
        bundle.putString(key, Uri.encode(Json.encodeToString(value)))
    }

    override fun serializeAsValue(value: OrderInfoRequestModel): String {
        return Uri.encode(Json.encodeToString(value))
    }
}