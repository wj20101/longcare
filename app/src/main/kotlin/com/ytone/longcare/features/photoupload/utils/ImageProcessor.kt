package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.ytone.longcare.R // 确保 R 文件被正确导入
import com.ytone.longcare.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.withTranslation
import androidx.core.graphics.createBitmap

/**
 * 图片处理工具类
 * 负责添加水印、缩放图片等功能
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val MAX_IMAGE_SIZE = 1920 // 最大图片尺寸
    }

    suspend fun processImage(
        originalUri: Uri,
        watermarkLines: List<String>
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val originalBitmap = loadBitmapFromUri(originalUri)
                ?: return@withContext Result.failure(Exception("无法读取图片"))
            val scaledBitmap = scaleImageIfNeeded(originalBitmap)
            val watermarkedBitmap = addWatermark(scaledBitmap, watermarkLines) // 调用新的 addWatermark
            val savedUri = saveBitmapToCache(watermarkedBitmap)

            if (originalBitmap != scaledBitmap) {
                originalBitmap.recycle()
            }
            scaledBitmap.recycle()
            // 注意：addWatermark 现在返回的是一个新的 Bitmap，也需要回收
            watermarkedBitmap.recycle()

            Result.success(savedUri)
        } catch (e: Exception) {
            logE(message = "processImage fail,uri is $originalUri", throwable = e)
            Result.failure(e)
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
     * 为图片添加一个复杂的水印，实现效果图样式。
     *
     * @param bitmap 要添加水印的原始图片。
     * @param watermarkLines 水印内容的字符串列表。
     * @return 添加了水印的新 Bitmap 对象。
     */
    private fun addWatermark(bitmap: Bitmap, watermarkLines: List<String>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val imageWidth = result.width
        val imageHeight = result.height

        // --- 1. 定义绘制参数 ---
        val logoBitmap = drawableToBitmap(R.mipmap.app_logo_round)
        val lineIndicator = ContextCompat.getDrawable(context, R.drawable.watermark_indicator_line)

        val textSize = 56f
        val textColor = Color.WHITE
        val horizontalPadding = 30f
        val bottomPadding = 30f
        val logoTextSpacing = 25f
        val lineSpacingMultiplier = 1.3f
        val lineIndicatorWidth = 8f
        val lineIndicatorTextSpacing = 20f

        // --- 2. 创建 Paint 和 TextPaint ---
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            // 为了让文字在任何背景下都清晰可见，添加一层细微的阴影
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
        }

        // --- 3. 准备文本并计算布局 ---
        val fullText = watermarkLines.joinToString("\n")
        val textMaxWidth = (imageWidth * 0.6f - horizontalPadding - lineIndicatorWidth - lineIndicatorTextSpacing).toInt()

        val textLayout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, textMaxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()

        // --- 4. 计算所有元素的尺寸和位置 ---
        val logoHeight = logoBitmap?.height ?: 0
        val textHeight = textLayout.height
        val contentTotalHeight = logoHeight + logoTextSpacing + textHeight

        val logoX = horizontalPadding
        val logoY = imageHeight - bottomPadding - contentTotalHeight

        val lineIndicatorTop = logoY + logoHeight + logoTextSpacing
        val lineIndicatorBottom = lineIndicatorTop + textHeight
        lineIndicator?.setBounds(
            horizontalPadding.toInt(),
            lineIndicatorTop.toInt(),
            (horizontalPadding + lineIndicatorWidth).toInt(),
            lineIndicatorBottom.toInt()
        )

        val textX = horizontalPadding + lineIndicatorWidth + lineIndicatorTextSpacing
        val textY = lineIndicatorTop

        // --- 5. 按顺序绘制 ---

        logoBitmap?.let { canvas.drawBitmap(it, logoX, logoY, null) }
        lineIndicator?.draw(canvas)

        canvas.withTranslation(textX, textY) {
            textLayout.draw(this)
        }

        return result
    }
}