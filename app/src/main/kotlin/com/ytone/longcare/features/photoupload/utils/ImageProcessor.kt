package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.graphics.BitmapCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.FileProviderHelper
import com.ytone.longcare.common.utils.SystemConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片处理工具类
 * 负责添加水印、缩放图片等功能
 */
@Singleton
class ImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val systemConfigManager: SystemConfigManager,
    private val imageLoader: ImageLoader
) {

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
        // 检查并转换硬件位图为软件位图（API 26+才支持HARDWARE配置）
        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        
        val width = softwareBitmap.width
        val height = softwareBitmap.height
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return softwareBitmap
        }
        val scale = minOf(MAX_IMAGE_SIZE.toFloat() / width, MAX_IMAGE_SIZE.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return BitmapCompat.createScaledBitmap(softwareBitmap, newWidth, newHeight, null, true)
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        // 使用FileProviderHelper创建处理后图片的文件
        val file = FileProviderHelper.createProcessedImageFile(context)
        
        // 保存bitmap到文件
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // 返回FileProvider Uri
        return FileProviderHelper.getUriForFile(context, file)
    }

    /**
     * 加载logo图片，优先使用远端图片，失败时使用本地默认图片
     * 优化缓存策略，提升性能和用户体验
     * @param targetImageWidth 目标图片宽度，用于计算合适的logo尺寸
     * @return 处理后的bitmap
     */
    private suspend fun loadLogoBitmap(targetImageWidth: Int): Bitmap? {
        return try {
            // 尝试从SystemConfigManager获取远端logo图片URL
            val logoUrl = systemConfigManager.getSyLogoImg()
            
            if (logoUrl.isNotEmpty()) {
                // 使用优化的ImageRequest配置
                val request = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .build()
                
                val result = imageLoader.execute(request)
                result.image?.let { image -> 
                    val bitmap = image.toBitmap()
                    return scaleBitmapToTargetSize(bitmap, targetImageWidth)
                }
            }
            
            // 远端图片加载失败，使用本地默认图片
            drawableToBitmap(R.drawable.app_watermark_image, targetImageWidth)
        } catch (e: Exception) {
            logE(message = "加载远端logo失败，使用本地默认图片", throwable = e)
            // 异常时使用本地默认图片
            drawableToBitmap(R.drawable.app_watermark_image, targetImageWidth)
        }
    }
    
    /**
     * 将drawable转换为bitmap，并根据图片尺寸控制logo大小
     * @param drawableId drawable资源ID
     * @param targetImageWidth 目标图片宽度，用于计算合适的logo尺寸
     * @return 处理后的bitmap
     */
    private fun drawableToBitmap(drawableId: Int, targetImageWidth: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        
        // 根据图片宽度动态计算logo尺寸
        // logo宽度为图片宽度的8-12%，确保在不同尺寸图片上都有合适的显示效果
        val logoMaxWidth = (targetImageWidth * 0.3f).toInt()
        val originalWidth = drawable.intrinsicWidth
        val originalHeight = drawable.intrinsicHeight
        
        // 如果原始logo太大，按比例缩放
        val (finalWidth, finalHeight) = if (originalWidth > logoMaxWidth) {
            val scale = logoMaxWidth.toFloat() / originalWidth
            Pair((originalWidth * scale).toInt(), (originalHeight * scale).toInt())
        } else {
            // 如果原始logo太小，适当放大，但不超过最大限制
            val minLogoWidth = (targetImageWidth * 0.3f).toInt()
            if (originalWidth < minLogoWidth) {
                val scale = minLogoWidth.toFloat() / originalWidth
                Pair((originalWidth * scale).toInt(), (originalHeight * scale).toInt())
            } else {
                Pair(originalWidth, originalHeight)
            }
        }
        
        val bitmap = createBitmap(finalWidth, finalHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, finalWidth, finalHeight)
        drawable.draw(canvas)
        return bitmap
    }
    
    /**
     * 将bitmap缩放到目标尺寸
     * @param bitmap 原始bitmap
     * @param targetImageWidth 目标图片宽度
     * @return 缩放后的bitmap
     */
    private fun scaleBitmapToTargetSize(bitmap: Bitmap, targetImageWidth: Int): Bitmap {
        // 检查并转换硬件位图为软件位图（API 26+才支持HARDWARE配置）
        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        
        // 根据图片宽度动态计算logo尺寸
        val logoMaxWidth = (targetImageWidth * 0.3f).toInt()
        val originalWidth = softwareBitmap.width
        val originalHeight = softwareBitmap.height
        
        // 如果原始logo太大，按比例缩放
        val (finalWidth, finalHeight) = if (originalWidth > logoMaxWidth) {
            val scale = logoMaxWidth.toFloat() / originalWidth
            Pair((originalWidth * scale).toInt(), (originalHeight * scale).toInt())
        } else {
            // 如果原始logo太小，适当放大，但不超过最大限制
            val minLogoWidth = (targetImageWidth * 0.3f).toInt()
            if (originalWidth < minLogoWidth) {
                val scale = minLogoWidth.toFloat() / originalWidth
                Pair((originalWidth * scale).toInt(), (originalHeight * scale).toInt())
            } else {
                Pair(originalWidth, originalHeight)
            }
        }
        
        return if (finalWidth != originalWidth || finalHeight != originalHeight) {
            BitmapCompat.createScaledBitmap(softwareBitmap, finalWidth, finalHeight, null, true)
        } else {
            softwareBitmap
        }
    }

    /**
     * 为图片添加完整的水印效果。
     */
    private suspend fun addWatermark(bitmap: Bitmap, watermarkLines: List<String>): Bitmap {
        // 检查并转换硬件位图为软件位图（API 26+才支持HARDWARE配置）
        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        
        val result = softwareBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val imageWidth = result.width
        val imageHeight = result.height

        var logoBitmap: Bitmap? = null
        try {
            // 尝试加载远端logo图片，失败时使用本地默认图片
            logoBitmap = loadLogoBitmap(imageWidth)
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

            logoBitmap?.let { logo ->
                // 确保logo也是软件位图
                val softwareLogo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && logo.config == Bitmap.Config.HARDWARE) {
                    logo.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    logo
                }
                canvas.drawBitmap(softwareLogo, horizontalPadding, currentY, null)
                currentY += softwareLogo.height + logoTextSpacing
                
                // 如果创建了副本，需要回收
                if (softwareLogo != logo) {
                    softwareLogo.recycle()
                }
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