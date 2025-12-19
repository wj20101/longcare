package com.ytone.longcare.features.photoupload.model

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

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
    val isUploaded: Boolean = false, // 标识是否已经上传到云端
    val key: String? = null, // 云端Key
    val cloudUrl: String? = null // 云端URL
) : Parcelable

/**
 * 图片处理任务类型枚举
 */
@Keep
enum class ImageTaskType {
    @Json(name = "BEFORE_CARE")
    BEFORE_CARE,  // 护理前
    
    @Json(name = "CENTER_CARE")
    CENTER_CARE,  // 护理中
    
    @Json(name = "AFTER_CARE")
    AFTER_CARE    // 护理后
}

/**
 * 图片处理任务状态枚举
 */
@Keep
enum class ImageTaskStatus {
    @Json(name = "PROCESSING")
    PROCESSING,  // 处理中
    
    @Json(name = "SUCCESS")
    SUCCESS,     // 成功
    
    @Json(name = "FAILED")
    FAILED       // 失败
}