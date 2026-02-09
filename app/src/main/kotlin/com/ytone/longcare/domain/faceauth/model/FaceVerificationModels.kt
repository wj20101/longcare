package com.ytone.longcare.domain.faceauth.model

const val FACE_AUTH_API_VERSION = "1.0.0"
const val FACE_AUTH_SOURCE_PHOTO_TYPE_HD = "2"

/**
 * 人脸验证错误（SDK无关领域模型）
 */
data class FaceVerifyError(
    val domain: String? = null,
    val code: String? = null,
    val description: String? = null,
    val reason: String? = null
)

/**
 * 人脸验证结果（SDK无关领域模型）
 */
data class FaceVerifyResult(
    val isSuccess: Boolean,
    val error: FaceVerifyError? = null
)
