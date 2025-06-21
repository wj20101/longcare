package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.ytone.longcare.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.scale

/**
 * 图片处理工具类
 * 负责添加水印、缩放图片等功能
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val MAX_IMAGE_SIZE = 1920 // 最大图片尺寸
        private const val WATERMARK_MARGIN = 40f // 水印边距
        private const val BASE_TEXT_SIZE = 36f // 基础文字大小
        private const val MIN_TEXT_SIZE = 24f // 最小文字大小
    }

    /**
     * 处理图片：添加水印并保存到缓存目录
     */
    suspend fun processImage(
        originalUri: Uri,
        watermarkContent: String = "长护险护理服务"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // 读取原始图片
            val originalBitmap = loadBitmapFromUri(originalUri)
                ?: return@withContext Result.failure(Exception("无法读取图片"))

            // 缩放图片（如果需要）
            val scaledBitmap = scaleImageIfNeeded(originalBitmap)

            // 添加水印
            val watermarkedBitmap = addWatermark(scaledBitmap, watermarkContent)

            // 保存到缓存目录
            val savedUri = saveBitmapToCache(watermarkedBitmap)

            // 清理资源
            if (originalBitmap != scaledBitmap) {
                originalBitmap.recycle()
            }
            scaledBitmap.recycle()
            watermarkedBitmap.recycle()

            Result.success(savedUri)
        } catch (e: Exception) {
            logE(message = "processImage fail,uri is $originalUri", throwable = e)
            Result.failure(e)
        }
    }

    /**
     * 从Uri加载Bitmap
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 先获取图片尺寸
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // 计算采样率
                val sampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)

                // 重新打开流并解码
                context.contentResolver.openInputStream(uri)?.use { newInputStream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(newInputStream, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            logE(message = "loadBitmapFromUri fail,uri is $uri", throwable = e)
            null
        }
    }

    /**
     * 计算采样率
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 如果需要则缩放图片
     */
    private fun scaleImageIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }

        val scale = minOf(
            MAX_IMAGE_SIZE.toFloat() / width,
            MAX_IMAGE_SIZE.toFloat() / height
        )

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    /**
     * 添加水印
     */
    private fun addWatermark(bitmap: Bitmap, watermarkContent: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 计算文字大小（根据图片大小动态调整）
        val textSize = calculateTextSize(bitmap.width, bitmap.height)

        // 创建画笔
        val paint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            isAntiAlias = true
            style = Paint.Style.FILL
            setShadowLayer(4f, 2f, 2f, Color.BLACK) // 添加阴影
        }

        // 水印文字内容
        val watermarkLines = getWatermarkText(watermarkContent)

        // 计算文字位置（左下角）
        val lineHeight = paint.fontMetrics.let { it.bottom - it.top }
        val totalTextHeight = lineHeight * watermarkLines.size

        var y = bitmap.height - WATERMARK_MARGIN - totalTextHeight + lineHeight

        // 绘制每行文字
        watermarkLines.forEach { line ->
            canvas.drawText(line, WATERMARK_MARGIN, y, paint)
            y += lineHeight
        }

        return result
    }

    /**
     * 计算文字大小
     */
    private fun calculateTextSize(imageWidth: Int, imageHeight: Int): Float {
        val imageSize = minOf(imageWidth, imageHeight)
        val textSize = (imageSize * 0.03f).coerceAtLeast(MIN_TEXT_SIZE)
        return minOf(textSize, BASE_TEXT_SIZE)
    }

    /**
     * 获取水印文字内容
     */
    private fun getWatermarkText(watermarkContent: String): List<String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())

        return listOf(
            watermarkContent,
            "拍摄时间：$currentTime",
            "GPS定位：已获取"
        )
    }

    /**
     * 保存Bitmap到缓存目录并返回Uri
     */
    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val cacheDir = File(context.cacheDir, "processed_images")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val fileName = "processed_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}