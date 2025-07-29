package com.ytone.longcare.common.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider 统一管理工具类
 * 避免在代码中硬编码包名和authorities
 */
object FileProviderHelper {
    
    /**
     * FileProvider的authorities
     */
    private fun getAuthorities(context: Context): String {
        return "${context.packageName}.fileprovider"
    }
    
    /**
     * 为文件创建Uri
     * @param context 上下文
     * @param file 文件
     * @return 文件的Uri
     */
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            getAuthorities(context),
            file
        )
    }
    
    /**
     * 创建相机拍照的临时文件Uri
     * @param context 上下文
     * @param fileName 文件名，如果为null则自动生成
     * @return 文件Uri
     */
    fun createCameraPhotoUri(context: Context, fileName: String? = null): Uri {
        val photoFile = File(
            context.cacheDir,
            fileName ?: "camera_photo_${System.currentTimeMillis()}.jpg"
        )
        return getUriForFile(context, photoFile)
    }
    
    /**
     * 创建处理后图片的临时文件Uri
     * @param context 上下文
     * @param fileName 文件名，如果为null则自动生成
     * @return 文件Uri
     */
    fun createProcessedImageUri(context: Context, fileName: String? = null): Uri {
        val cacheDir = File(context.cacheDir, "processed_images")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val imageFile = File(
            cacheDir,
            fileName ?: "processed_${System.currentTimeMillis()}.jpg"
        )
        return getUriForFile(context, imageFile)
    }
    
    /**
     * 创建处理后图片的临时文件
     * @param context 上下文
     * @param fileName 文件名，如果为null则自动生成
     * @return 文件对象
     */
    fun createProcessedImageFile(context: Context, fileName: String? = null): File {
        val cacheDir = File(context.cacheDir, "processed_images")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        return File(
            cacheDir,
            fileName ?: "processed_${System.currentTimeMillis()}.jpg"
        )
    }
    
    /**
     * 获取FileProvider的authorities字符串
     * @param context 上下文
     * @return authorities字符串
     */
    fun getFileProviderAuthorities(context: Context): String {
        return getAuthorities(context)
    }
}