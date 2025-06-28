package com.ytone.longcare.common.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Uri扩展函数
 */

/**
 * 根据Uri获取文件大小
 * @param context 上下文
 * @return 文件大小（字节），如果无法获取则返回0
 */
fun Uri.getFileSize(context: Context): Long {
    return when (scheme) {
        "file" -> {
            // 文件URI，直接通过File获取大小
            path?.let { File(it).length() } ?: 0L
        }

        "content" -> {
            // 内容URI，通过ContentResolver获取大小
            try {
                context.contentResolver.openAssetFileDescriptor(this, "r")?.use { descriptor ->
                    descriptor.length
                } ?: 0L
            } catch (e: Exception) {
                // 如果无法通过AssetFileDescriptor获取，尝试通过Cursor查询
                try {
                    context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && cursor.moveToFirst()) {
                            cursor.getLong(sizeIndex)
                        } else {
                            0L
                        }
                    } ?: 0L
                } catch (ex: Exception) {
                    0L
                }
            }
        }

        else -> 0L
    }
}

/**
 * 根据Uri获取文件名
 * @param context 上下文
 * @return 文件名，如果无法获取则返回"unknown"
 */
fun Uri.getFileName(context: Context, defaultFileName: String = "unknown"): String {
    return when (scheme) {
        "file" -> {
            // 文件URI，直接从路径获取文件名
            lastPathSegment ?: defaultFileName
        }

        "content" -> {
            // 内容URI，通过ContentResolver查询文件名
            try {
                context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else {
                        lastPathSegment ?: defaultFileName
                    }
                } ?: (lastPathSegment ?: defaultFileName)
            } catch (e: Exception) {
                lastPathSegment ?: defaultFileName
            }
        }

        else -> lastPathSegment ?: defaultFileName
    }
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