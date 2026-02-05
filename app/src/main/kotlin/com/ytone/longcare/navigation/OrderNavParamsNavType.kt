package com.ytone.longcare.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import com.ytone.longcare.common.utils.logE
import kotlinx.serialization.json.Json

/**
 * 自定义 NavType 用于处理 OrderNavParams 类型的序列化和反序列化
 */
object OrderNavParamsNavType : NavType<OrderNavParams>(isNullableAllowed = false) {

    override fun get(bundle: Bundle, key: String): OrderNavParams? {
        return bundle.getString(key)?.let { jsonString ->
            try {
                Json.decodeFromString<OrderNavParams>(Uri.decode(jsonString))
            } catch (e: Exception) {
                logE(message = "OrderNavParamsNavType get fail", throwable = e)
                null
            }
        }
    }

    override fun parseValue(value: String): OrderNavParams {
        return try {
            Json.decodeFromString<OrderNavParams>(Uri.decode(value))
        } catch (e: Exception) {
            logE(message = "OrderNavParamsNavType parseValue fail", throwable = e)
            throw IllegalArgumentException("Failed to parse OrderNavParams", e)
        }
    }

    override fun put(bundle: Bundle, key: String, value: OrderNavParams) {
        bundle.putString(key, Uri.encode(Json.encodeToString(value)))
    }

    override fun serializeAsValue(value: OrderNavParams): String {
        return Uri.encode(Json.encodeToString(value))
    }
}
