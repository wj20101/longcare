package com.ytone.longcare.features.photoupload.model

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * ImageTaskType 的 Moshi 适配器
 * 用于处理枚举类型在 Map 中作为 key 时的序列化和反序列化
 */
class ImageTaskTypeAdapter {
    
    @ToJson
    fun toJson(taskType: ImageTaskType): String {
        return when (taskType) {
            ImageTaskType.BEFORE_CARE -> "BEFORE_CARE"
            ImageTaskType.AFTER_CARE -> "AFTER_CARE"
        }
    }
    
    @FromJson
    fun fromJson(value: String): ImageTaskType {
        return when (value) {
            "BEFORE_CARE" -> ImageTaskType.BEFORE_CARE
            "AFTER_CARE" -> ImageTaskType.AFTER_CARE
            else -> throw IllegalArgumentException("Unknown ImageTaskType: $value")
        }
    }
}