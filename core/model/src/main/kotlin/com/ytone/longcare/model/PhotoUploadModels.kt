package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

/**
 * 水印数据模型
 */
@Serializable
data class WatermarkData(
    val title: String,
    val insuredPerson: String,
    val caregiver: String,
    val address: String
)

/**
 * 图片处理任务数据模型
 */
@JsonClass(generateAdapter = true)
data class ImageTask(
    val id: String,
    val originalUri: String,
    val taskType: ImageTaskType,
    val resultUri: String? = null,
    val status: ImageTaskStatus = ImageTaskStatus.PROCESSING,
    val errorMessage: String? = null,
    val isUploaded: Boolean = false,
    val key: String? = null,
    val cloudUrl: String? = null
) : java.io.Serializable

/**
 * 图片处理任务类型枚举
 */
enum class ImageTaskType {
    @Json(name = "BEFORE_CARE")
    BEFORE_CARE,

    @Json(name = "CENTER_CARE")
    CENTER_CARE,

    @Json(name = "AFTER_CARE")
    AFTER_CARE
}

/**
 * 图片处理任务状态枚举
 */
enum class ImageTaskStatus {
    @Json(name = "PROCESSING")
    PROCESSING,

    @Json(name = "SUCCESS")
    SUCCESS,

    @Json(name = "FAILED")
    FAILED
}
