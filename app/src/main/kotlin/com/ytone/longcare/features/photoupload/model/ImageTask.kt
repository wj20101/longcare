package com.ytone.longcare.features.photoupload.model

import android.net.Uri

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
    val errorMessage: String? = null
)

/**
 * 图片处理任务类型枚举
 */
enum class ImageTaskType {
    BEFORE_CARE,  // 护理前
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