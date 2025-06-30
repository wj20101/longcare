package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 图片处理工具类
 * 负责添加水印、缩放图片等功能
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val MAX_IMAGE_SIZE = 1920 // 图片最大尺寸，防止OOM
    }

    /**
     * 核心处理流程：加载、缩放、添加水印、保存。
     * 使用 try...finally 确保所有中间创建的 Bitmap 都被正确回收。
     */
    suspend fun processImage(originalUri: Uri, watermarkLines: List<String>): Result<Uri> =
        withContext(Dispatchers.IO) {
            var originalBitmap: Bitmap? = null
            var scaledBitmap: Bitmap? = null
            var watermarkedBitmap: Bitmap? = null

            try {
                originalBitmap = loadBitmapFromUri(originalUri)
                    ?: return@withContext Result.failure(Exception("无法从URI加载图片: $originalUri"))

                scaledBitmap = scaleImageIfNeeded(originalBitmap)
                watermarkedBitmap = addWatermark(scaledBitmap, watermarkLines)

                val savedUri = saveBitmapToCache(watermarkedBitmap)
                Result.success(savedUri)

            } catch (e: Exception) {
                logE(message = "processImage 失败, uri is $originalUri", throwable = e)
                Result.failure(e)
            } finally {
                // 在 finally 块中手动回收所有创建的 Bitmap，确保内存安全
                originalBitmap?.recycle()
                // 如果 scaledBitmap 和 originalBitmap 是同一个对象，则不重复回收
                if (scaledBitmap != originalBitmap) {
                    scaledBitmap?.recycle()
                }
                watermarkedBitmap?.recycle()
            }
        }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                val sampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
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

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleImageIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }
        val scale = minOf(MAX_IMAGE_SIZE.toFloat() / width, MAX_IMAGE_SIZE.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return bitmap.scale(newWidth, newHeight, false)
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val cacheDir = File(context.cacheDir, "processed_images")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val fileName = "processed_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.toUri()
    }

    private fun drawableToBitmap(drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * 为图片添加完整的水印效果。
     */
    private fun addWatermark(bitmap: Bitmap, watermarkLines: List<String>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val imageWidth = result.width
        val imageHeight = result.height

        var logoBitmap: Bitmap? = null
        try {
            logoBitmap = drawableToBitmap(R.mipmap.app_logo_round)
            val lineIndicator =
                ContextCompat.getDrawable(context, R.drawable.watermark_indicator_line)

            val textSize = 56f
            val textColor = Color.WHITE
            val horizontalPadding = 30f
            val bottomPadding = 30f
            val logoTextSpacing = 25f
            val lineSpacing = 15f
            val lineIndicatorWidth = 8f
            val lineIndicatorTextSpacing = 20f

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                this.textSize = textSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setShadowLayer(5f, 2f, 2f, Color.BLACK)
            }

            // 文本最大宽度现在严格等于图片宽度的60%
            val textMaxWidth = (imageWidth * 0.6f).toInt()

            val layouts = watermarkLines.map { line ->
                createIndentedLayout(line, textPaint, textMaxWidth)
            }

            var totalContentHeight = layouts.sumOf { it.height }.toFloat()
            if (layouts.isNotEmpty()) {
                totalContentHeight += (layouts.size - 1) * lineSpacing
            }
            if (logoBitmap != null) {
                totalContentHeight += logoBitmap.height + logoTextSpacing
            }

            var currentY = imageHeight - bottomPadding - totalContentHeight

            logoBitmap?.let {
                canvas.drawBitmap(it, horizontalPadding, currentY, null)
                currentY += it.height + logoTextSpacing
            }

            // 绘制左侧装饰线
            val lineIndicatorTop = currentY
            val lineIndicatorBottom =
                currentY + layouts.sumOf { it.height }.toFloat() + (layouts.size - 1) * lineSpacing
            lineIndicator?.setBounds(
                horizontalPadding.toInt(),
                lineIndicatorTop.toInt(),
                (horizontalPadding + lineIndicatorWidth).toInt(),
                lineIndicatorBottom.toInt()
            )
            lineIndicator?.draw(canvas)

            // 逐个绘制文本布局
            layouts.forEach { layout ->
                val textX = horizontalPadding + lineIndicatorWidth + lineIndicatorTextSpacing
                canvas.withTranslation(textX, currentY) {
                    layout.draw(this)
                }
                currentY += layout.height + lineSpacing
            }

        } finally {
            // 回收在函数内部创建的 logoBitmap
            logoBitmap?.recycle()
        }

        return result
    }

    /**
     * 创建带有悬挂缩进的 StaticLayout，以实现冒号后对齐。
     */
    private fun createIndentedLayout(text: String, paint: TextPaint, maxWidth: Int): StaticLayout {
        val colonIndex = text.indexOf(':')
        if (colonIndex < 0 || colonIndex >= text.length - 1) {
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth).build()
        }
        var contentStartIndex = colonIndex + 1
        while (contentStartIndex < text.length && text[contentStartIndex] == ' ') {
            contentStartIndex++
        }
        val labelPart = text.substring(0, contentStartIndex)
        val indentation = paint.measureText(labelPart)

        return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .setIndents(intArrayOf(0, indentation.toInt()), null)
            .build()
    }
}