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
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

/**
 * 图片处理工具类
 * 负责添加水印、缩放图片等功能
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val MAX_IMAGE_SIZE = 1920 // 最大图片尺寸
    }

    suspend fun processImage(originalUri: Uri, watermarkLines: List<String>): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val originalBitmap = loadBitmapFromUri(originalUri)
                ?: return@withContext Result.failure(Exception("无法读取图片"))
            val scaledBitmap = scaleImageIfNeeded(originalBitmap)
            val watermarkedBitmap = addWatermark(scaledBitmap, watermarkLines)
            val savedUri = saveBitmapToCache(watermarkedBitmap)

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

    private fun addWatermark(bitmap: Bitmap, watermarkLines: List<String>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val imageWidth = result.width
        val imageHeight = result.height

        val logoBitmap = drawableToBitmap(R.mipmap.app_logo_round)
        val textSize = 56f
        val textColor = Color.WHITE
        val horizontalPadding = 30f
        val bottomPadding = 30f
        val logoTextSpacing = 25f
        val lineSpacing = 15f

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
        }

        val textMaxWidth = (imageWidth * 0.9f - horizontalPadding).toInt()

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

        layouts.forEach { layout ->
            canvas.withTranslation(horizontalPadding, currentY) {
                layout.draw(this)
            }

            currentY += layout.height + lineSpacing
        }

        return result
    }

    /**
     * 创建带有悬挂缩进的 StaticLayout，以实现冒号后对齐。
     */
    private fun createIndentedLayout(text: String, paint: TextPaint, maxWidth: Int): StaticLayout {
        // 查找第一个冒号的位置
        val colonIndex = text.indexOf(':')

        // 如果没有冒号，或者冒号是最后一个字符，则不进行特殊缩进处理
        if (colonIndex < 0 || colonIndex >= text.length - 1) {
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth).build()
        }

        // ==========================================================
        // 核心修正：测量标签+冒号+空格的宽度作为缩进量
        // ==========================================================
        // 我们要测量的部分是冒号以及它后面的所有空格，直到第一个非空格字符
        var contentStartIndex = colonIndex + 1
        while (contentStartIndex < text.length && text[contentStartIndex] == ' ') {
            contentStartIndex++
        }
        // "labelPart" 现在是 "地址: "
        val labelPart = text.substring(0, contentStartIndex)
        val indentation = paint.measureText(labelPart)

        return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f) // 默认行间距
            .setIncludePad(false)
            // 关键：设置缩进
            // 第一行缩进为0，后续所有行都缩进 "labelPart" 的宽度
            .setIndents(intArrayOf(0, indentation.toInt()), null)
            .build()
    }
}