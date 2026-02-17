package com.ytone.longcare.model

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 水印数据模型
 */
@Keep
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
@Keep
@Parcelize
@JsonClass(generateAdapter = true)
data class ImageTask(
    val id: String,
    val originalUri: Uri,
    val taskType: ImageTaskType,
    val resultUri: Uri? = null,
    val status: ImageTaskStatus = ImageTaskStatus.PROCESSING,
    val errorMessage: String? = null,
    val isUploaded: Boolean = false,
    val key: String? = null,
    val cloudUrl: String? = null
) : Parcelable

/**
 * 图片处理任务类型枚举
 */
@Keep
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
@Keep
enum class ImageTaskStatus {
    @Json(name = "PROCESSING")
    PROCESSING,

    @Json(name = "SUCCESS")
    SUCCESS,

    @Json(name = "FAILED")
    FAILED
}
