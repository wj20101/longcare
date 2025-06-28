package com.ytone.longcare.common.utils

import android.content.Context
import android.net.Uri

/**
 * 根据Uri获取文件大小
 * @param context 上下文
 * @return 文件大小（字节），如果无法获取则返回0
 */
fun Uri.getFileSize(context: Context): Long {
    return CosUtils.getFileSize(context,this)
}

/**
 * 根据Uri获取文件名
 * @param context 上下文
 * @return 文件名，如果无法获取则返回"unknown"
 */
fun Uri.getFileName(context: Context, defaultFileName: String = "unknown"): String {
    return CosUtils.getFileName(context, this, defaultFileName)
}

/**
 * 根据Uri获取文件扩展名
 * @param context 上下文
 * @param defaultExtension 默认扩展名，当无法获取时使用
 * @return 文件扩展名（不包含点号）
 */
fun Uri.getFileExtension(context: Context, defaultExtension: String = "jpg"): String {
    val fileName = getFileName(context)
    return fileName.substringAfterLast('.', defaultExtension)
}