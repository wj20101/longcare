package com.ytone.longcare.common.constants

/**
 * COS存储相关常量定义
 * 用于统一管理文件上传相关的常量
 */
object CosConstants {
    
    /**
     * 默认文件夹类型
     * 用于服务图片上传
     */
    const val DEFAULT_FOLDER_TYPE = 13

    /**
     * 用于人脸照片上传
     */
    const val DEFAULT_FACE_TYPE = 14

    /** 业务图片上传前允许的最大文件大小。 */
    const val MAX_IMAGE_FILE_SIZE_BYTES = 10L * 1024L * 1024L

    // 可以在这里添加其他COS相关的常量
    // const val ALLOWED_IMAGE_TYPES = "image/jpeg,image/png,image/webp"
}
