package com.ytone.longcare.common.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.ytone.longcare.data.cos.model.UploadParams
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * COS工具类
 * 提供文件上传相关的便捷方法
 */
object CosUtils {

    /**
     * 创建上传参数
     * @param context 上下文
     * @param fileUri 文件Uri
     * @param folderType 文件夹类型
     * @param keyPrefix 文件key前缀，默认为空
     * @return 上传参数
     */
    fun createUploadParams(
        context: Context,
        fileUri: Uri,
        folderType: Int,
        keyPrefix: String = "",
    ): UploadParams {
        val fileName = fileUri.getFileName(context)
        val key = generateFileKey(keyPrefix, fileUri)
        val extension = fileName.substringAfterLast('.', "")
        val contentType = getContentType(extension)

        return UploadParams(
            fileUri = fileUri,
            key = key,
            folderType = folderType,
            contentType = contentType
        )
    }

    /**
     * 生成文件键名
     * @param fileKeyPre 文件key前缀,上传时使用这个前缀+唯一文件名+".后缀"作为完整key
     * @param uri 原始文件
     * @return 生成的键名
     */
    fun generateFileKey(fileKeyPre: String, uri: Uri): String {
        if (fileKeyPre.isEmpty()) return ""
        // 生成唯一标识符（时间戳 + UUID前8位）
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        val uniqueId = "${timestamp}_${uuid}"

        // 获取文件扩展名
        val fileName = uri.lastPathSegment ?: "file"
        val extension = fileName.substringAfterLast('.', "")

        // 构建完整的键名
        return if (extension.isNotEmpty()) {
            "${fileKeyPre}${uniqueId}.${extension}"
        } else {
            "${fileKeyPre}${uniqueId}"
        }
    }
    
    /**
     * 根据文件扩展名获取MIME类型
     * @param extension 文件扩展名
     * @return MIME类型
     */
    fun getContentType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "avi" -> "video/avi"
            "mov" -> "video/quicktime"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }
    }

    /**
     * 从Uri获取文件路径
     * @param context 上下文
     * @param uri 文件Uri
     * @return 文件路径，如果无法获取返回null
     */
    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    // 对于content://类型的Uri，需要复制到临时文件
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = createTempFile(context, uri)
                        tempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        tempFile.absolutePath
                    } else {
                        null
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建临时文件
     * @param context 上下文
     * @param uri 原始Uri
     * @return 临时文件
     */
    private fun createTempFile(context: Context, uri: Uri): File {
        val fileName = uri.getFileName(context)
        val tempDir = File(context.cacheDir, "cos_temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return File(tempDir, fileName)
    }

    /**
     * 从Uri获取文件名
     * @param context 上下文
     * @param uri 文件Uri
     * @return 文件名
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 格式化文件大小
     * @param bytes 字节数
     * @return 格式化后的文件大小字符串
     */
    fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return String.format(Locale.getDefault(), "%.2f %s", size, units[unitIndex])
    }

    /**
     * 验证文件是否为图片
     * @param filePath 文件路径
     * @return 是否为图片
     */
    fun isImageFile(filePath: String): Boolean {
        val extension = File(filePath).extension.lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }

    /**
     * 验证文件是否为视频
     * @param filePath 文件路径
     * @return 是否为视频
     */
    fun isVideoFile(filePath: String): Boolean {
        val extension = File(filePath).extension.lowercase()
        return extension in listOf("mp4", "avi", "mov", "wmv", "flv", "mkv", "webm")
    }

    /**
     * 清理临时文件
     * @param context 上下文
     */
    fun cleanTempFiles(context: Context) {
        try {
            val tempDir = File(context.cacheDir, "cos_temp")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }

    /**
     * 生成缩略图键名
     * @param originalKey 原始文件键名
     * @param suffix 缩略图后缀，默认为"_thumb"
     * @return 缩略图键名
     */
    fun generateThumbnailKey(originalKey: String, suffix: String = "_thumb"): String {
        val file = File(originalKey)
        val nameWithoutExtension = file.nameWithoutExtension
        val extension = file.extension
        val parentPath = file.parent

        val thumbnailName = if (extension.isNotEmpty()) {
            "${nameWithoutExtension}${suffix}.${extension}"
        } else {
            "${nameWithoutExtension}${suffix}"
        }

        return if (parentPath != null && parentPath != ".") {
            "$parentPath/$thumbnailName"
        } else {
            thumbnailName
        }
    }

    /**
     * 根据Uri获取文件大小
     * @param context 上下文
     * @return 文件大小（字节），如果无法获取则返回0
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return when (uri.scheme) {
            "file" -> {
                // 文件URI，直接通过File获取大小
                uri.path?.let { File(it).length() } ?: 0L
            }

            "content" -> {
                // 内容URI，通过ContentResolver获取大小
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                        descriptor.length
                    } ?: 0L
                } catch (e: Exception) {
                    // 如果无法通过AssetFileDescriptor获取，尝试通过Cursor查询
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
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
    fun getFileName(context: Context, uri: Uri, defaultFileName: String = "unknown"): String {
        return when (uri.scheme) {
            "file" -> {
                // 文件URI，直接从路径获取文件名
                uri.lastPathSegment ?: defaultFileName
            }

            "content" -> {
                // 内容URI，通过ContentResolver查询文件名
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            cursor.getString(nameIndex)
                        } else {
                            uri.lastPathSegment ?: defaultFileName
                        }
                    } ?: (uri.lastPathSegment ?: defaultFileName)
                } catch (_: Exception) {
                    uri.lastPathSegment ?: defaultFileName
                }
            }

            else -> uri.lastPathSegment ?: defaultFileName
        }
    }
}