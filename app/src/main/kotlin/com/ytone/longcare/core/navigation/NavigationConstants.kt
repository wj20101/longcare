package com.ytone.longcare.core.navigation

/**
 * 定义了用于应用程序中导航参数和结果的常量键。
 * 这种集中式方法有助于防止拼写错误并简化与导航相关的数据传递的维护。
 */
object NavigationConstants {
    /**
     * 用于从 `SavedStateHandle` 检索照片上传过程结果的键。
     * 结果通常是一个包含成功上传图像的 `Map<ImageTaskType, List<ImageTask>>`。
     */
    const val PHOTO_UPLOAD_RESULT_KEY = "photo_upload_result"

    /**
     * 用于将现有图像任务传递到照片上传屏幕的键。
     * 数据是一个 `Map<ImageTaskType, List<ImageTask>>`，表示已与订单关联的图像。
     */
    const val EXISTING_IMAGES_KEY = "existing_images"

    /**
     * 用于通过 `SavedStateHandle` 从 `CameraScreen` 检索新捕获图像的 URI 的键。
     * 该值为图像 URI 的 `String` 表示形式。
     */
    const val CAPTURED_IMAGE_URI_KEY = "captured_image_uri"

    /**
     * 用于通过 `SavedStateHandle` 从 `ManualFaceCaptureScreen` 检索捕获的人脸图片路径的键。
     * 该值为图片文件路径的 `String` 表示形式。
     */
    const val FACE_IMAGE_PATH_KEY = "face_image_path"

    const val WATERMARK_DATA_KEY = "watermark_data_key"
}
