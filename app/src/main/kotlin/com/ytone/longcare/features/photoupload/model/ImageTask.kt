package com.ytone.longcare.features.photoupload.model

import android.net.Uri
import com.squareup.moshi.Json

/**
 * 图片处理任务数据模型
 */
data class ImageTask(
    val id: String,
    val originalUri: Uri,
    val taskType: ImageTaskType,
    val watermarkLines: List<String>,
    val resultUri: Uri? = null,
    val status: ImageTaskStatus = ImageTaskStatus.PROCESSING,
    val errorMessage: String? = null,
    val isUploaded: Boolean = false, // 标识是否已经上传到云端
    val cloudUrl: String? = null // 云端URL
)

/**
 * 图片处理任务类型枚举
 */
enum class ImageTaskType {
    @Json(name = "BEFORE_CARE")
    BEFORE_CARE,  // 护理前
    
    @Json(name = "AFTER_CARE")
    AFTER_CARE    // 护理后
}

/**
 * 图片处理任务状态枚举
 */
enum class ImageTaskStatus {
    PROCESSING,  // 处理中
    SUCCESS,     // 成功
    FAILED       // 失败
}