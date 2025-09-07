package com.ytone.longcare.core.navigation

/**
 * 导航相关的常量定义
 * 用于统一管理页面间数据传递的键值
 */
object NavigationConstants {
    
    /**
     * 图片上传结果的键值
     * 用于PhotoUploadScreen向上一个页面回传上传结果
     */
    const val PHOTO_UPLOAD_RESULT_KEY = "photo_upload_result"
    
    /**
     * 已有图片数据的键值
     * 用于向PhotoUploadScreen传递已有的图片数据
     * 数据类型: Map<ImageTaskType, List<ImageTask>>
     */
    const val EXISTING_IMAGES_KEY = "existing_images"
    
    // 可以在这里添加其他导航相关的常量
    // const val OTHER_RESULT_KEY = "other_result"
}