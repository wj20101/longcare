package com.ytone.longcare.di

import android.net.Uri
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import androidx.core.net.toUri

/**
 * 一个自定义的 Moshi JsonAdapter，用于处理 Android 的 Uri 类型。
 * 将 Uri 序列化为字符串，反序列化时从字符串创建 Uri。
 */
class UriJsonAdapter : JsonAdapter<Uri>() {
    
    @ToJson
    override fun toJson(writer: JsonWriter, value: Uri?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.toString())
        }
    }
    
    @FromJson
    override fun fromJson(reader: JsonReader): Uri? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            JsonReader.Token.STRING -> {
                val uriString = reader.nextString()
                if (uriString.isBlank()) {
                    null
                } else {
                    try {
                        uriString.toUri()
                    } catch (_: Exception) {
                        // 如果解析失败，返回 null
                        null
                    }
                }
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }
}