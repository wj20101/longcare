package com.ytone.longcare.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.features.photoupload.model.WatermarkData
import kotlinx.serialization.json.Json

/**
 * Custom NavType for handling serialization and deserialization of WatermarkData
 */
object WatermarkDataNavType : NavType<WatermarkData>(isNullableAllowed = false) {

    override fun get(bundle: Bundle, key: String): WatermarkData? {
        return bundle.getString(key)?.let { jsonString ->
            try {
                Json.decodeFromString<WatermarkData>(Uri.decode(jsonString))
            } catch (e: Exception) {
                logE(message = "WatermarkDataNavType get fail", throwable = e)
                null
            }
        }
    }

    override fun parseValue(value: String): WatermarkData {
        return try {
            Json.decodeFromString<WatermarkData>(Uri.decode(value))
        } catch (e: Exception) {
            logE(message = "WatermarkDataNavType parseValue fail", throwable = e)
            throw IllegalArgumentException("Failed to parse WatermarkData from value: $value", e)
        }
    }

    override fun put(bundle: Bundle, key: String, value: WatermarkData) {
        bundle.putString(key, Uri.encode(Json.encodeToString(value)))
    }

    override fun serializeAsValue(value: WatermarkData): String {
        return Uri.encode(Json.encodeToString(value))
    }
}