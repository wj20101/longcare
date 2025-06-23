package com.ytone.longcare.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import com.ytone.longcare.common.utils.logE
import kotlinx.serialization.json.Json

/**
 * 自定义 NavType 用于处理 EndOderInfo 类型的序列化和反序列化
 */
object EndOderInfoNavType : NavType<EndOderInfo?>(isNullableAllowed = true) {

    override fun get(bundle: Bundle, key: String): EndOderInfo? {
        return bundle.getString(key)?.let { jsonString ->
            try {
                Json.decodeFromString<EndOderInfo>(Uri.decode(jsonString))
            } catch (e: Exception) {
                logE(message = "EndOderInfoNavType get fail", throwable = e)
                null
            }
        }
    }

    override fun parseValue(value: String): EndOderInfo? {
        return if (value == "null" || value.isEmpty()) {
            null
        } else {
            try {
                Json.decodeFromString<EndOderInfo>(Uri.decode(value))
            } catch (e: Exception) {
                logE(message = "EndOderInfoNavType parseValue fail", throwable = e)
                null
            }
        }
    }

    override fun put(bundle: Bundle, key: String, value: EndOderInfo?) {
        if (value != null) {
            bundle.putString(key, Uri.encode(Json.encodeToString(value)))
        } else {
            bundle.putString(key, "null")
        }
    }

    override fun serializeAsValue(value: EndOderInfo?): String {
        return if (value != null) {
            Uri.encode(Json.encodeToString(value))
        } else {
            "null"
        }
    }
}