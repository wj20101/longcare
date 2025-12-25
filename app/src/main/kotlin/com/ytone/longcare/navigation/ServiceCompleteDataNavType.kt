package com.ytone.longcare.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import com.ytone.longcare.common.utils.logE
import kotlinx.serialization.json.Json

/**
 * 自定义 NavType 用于处理 ServiceCompleteData 类型的序列化和反序列化
 */
object ServiceCompleteDataNavType : NavType<ServiceCompleteData>(isNullableAllowed = false) {

    override fun get(bundle: Bundle, key: String): ServiceCompleteData? {
        return bundle.getString(key)?.let { jsonString ->
            try {
                Json.decodeFromString<ServiceCompleteData>(Uri.decode(jsonString))
            } catch (e: Exception) {
                logE(message = "ServiceCompleteDataNavType get fail", throwable = e)
                null
            }
        }
    }

    override fun parseValue(value: String): ServiceCompleteData {
        return try {
            Json.decodeFromString<ServiceCompleteData>(Uri.decode(value))
        } catch (e: Exception) {
            logE(message = "ServiceCompleteDataNavType parseValue fail", throwable = e)
            throw IllegalArgumentException("Failed to parse ServiceCompleteData from value: $value", e)
        }
    }

    override fun put(bundle: Bundle, key: String, value: ServiceCompleteData) {
        bundle.putString(key, Uri.encode(Json.encodeToString(value)))
    }

    override fun serializeAsValue(value: ServiceCompleteData): String {
        return Uri.encode(Json.encodeToString(value))
    }
}
