package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW

/**
 * 回调函数类型定义
 */
typealias FaceCaptureCallback = (bitmap: Bitmap, quality: Float) -> Unit
typealias ProcessingStateCallback = (isProcessing: Boolean) -> Unit
typealias HintCallback = (hint: String) -> Unit
typealias FaceDetectionCallback = (detected: Boolean, quality: Float) -> Unit

/**
 * 人脸捕获图像分析器
 * 使用ML Kit进行人脸检测，包含性能优化和质量评估
 * 
 * @param onFaceCaptured 人脸捕获回调
 * @param onProcessingStateChanged 处理状态变化回调
 * @param onHintChanged 提示信息变化回调
 * @param onFaceDetectionChanged 人脸检测状态变化回调
 * @param coroutineScope 协程作用域
 */
class FaceCaptureAnalyzer(
    private val onFaceCaptured: FaceCaptureCallback,
    private val onProcessingStateChanged: ProcessingStateCallback,
    private val onHintChanged: HintCallback,
    private val onFaceDetectionChanged: FaceDetectionCallback,
    private val coroutineScope: CoroutineScope
) : ImageAnalysis.Analyzer {

    // ML Kit人脸检测器配置
    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // 优化性能
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // 启用所有分类
        .setMinFaceSize(0.3f) // 增加最小人脸尺寸，提高质量
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE) // 不需要轮廓，提升性能
        .build()
        
    private val detector = FaceDetection.getClient(detectorOptions)
    
    // 性能优化参数
    private var frameCount = 0
    private val frameSkip = 3 // 跳帧优化，每3帧处理一次
    private var isProcessing = false

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // 防止重复处理
        if (isProcessing) {
            imageProxy.close()
            return
        }
        
        // 实现智能跳帧
        frameCount++
        if (frameCount % frameSkip != 0) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            isProcessing = true
            onProcessingStateChanged(true)
            
            val image = InputImage.fromMediaImage(
                mediaImage, 
                imageProxy.imageInfo.rotationDegrees
            )
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    // 使用同步处理避免生命周期问题
                    coroutineScope.launch(Dispatchers.Default) {
                        try {
                            processFaces(faces, imageProxy)
                        } catch (e: Exception) {
                            logE("Error processing faces", tag = "FaceCaptureAnalyzer", throwable = e)
                            onHintChanged("处理失败，请重试")
                            onFaceDetectionChanged(false, 0f)
                        } finally {
                            // 确保在处理完成后才关闭 ImageProxy
                            isProcessing = false
                            onProcessingStateChanged(false)
                            try {
                                imageProxy.close()
                            } catch (e: Exception) {
                                logW("Error closing ImageProxy", tag = "FaceCaptureAnalyzer", throwable = e)
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    logE("Face detection failed", tag = "FaceCaptureAnalyzer", throwable = exception)
                    onHintChanged("检测失败，请重试")
                    onFaceDetectionChanged(false, 0f)
                    isProcessing = false
                    onProcessingStateChanged(false)
                    try {
                        imageProxy.close()
                    } catch (e: Exception) {
                        logW("Error closing ImageProxy", tag = "FaceCaptureAnalyzer", throwable = e)
                    }
                }
        } else {
            imageProxy.close()
        }
    }
    
    /**
     * 处理检测到的人脸
     * @param faces 检测到的人脸列表
     * @param imageProxy 图像代理
     */
    private suspend fun processFaces(faces: List<Face>, imageProxy: ImageProxy) {
        if (faces.isEmpty()) {
            // 确保UI状态同步：未检测到人脸
            onFaceDetectionChanged(false, 0f)
            onHintChanged("未检测到人脸，请调整位置")
            return
        }
        
        // 选择质量最高的人脸
        val bestFace = faces.maxByOrNull { calculateFaceQuality(it) }
        bestFace?.let { face ->
            val quality = calculateFaceQuality(face)
            
            // 先更新人脸检测状态（显示绿框）
            onFaceDetectionChanged(true, quality)
            
            // 根据质量决定提示信息和是否捕获
            if (quality > 0.8f) {
                // 高质量人脸：显示积极提示并尝试捕获
                onHintChanged("检测到高质量人脸，正在捕获...")
                
                cropFaceFromImage(imageProxy, face.boundingBox)?.let { bitmap ->
                    onFaceCaptured(bitmap, quality)
                    onHintChanged("人脸捕获成功！")
                } ?: run {
                    onHintChanged("图像处理失败，请重试")
                }
            } else {
                // 低质量人脸：显示改进建议但仍然显示检测状态
                val hint = getQualityHint(face)
                onHintChanged(hint)
            }
        } ?: run {
            // 如果没有找到最佳人脸，重置状态
            onFaceDetectionChanged(false, 0f)
            onHintChanged("人脸检测异常，请重试")
        }
    }

    /**
     * 计算人脸质量分数
     * @param face 检测到的人脸
     * @return 质量分数 (0.0-1.0)
     */
    private fun calculateFaceQuality(face: Face): Float {
        var quality = 0f
        
        // 头部角度评分 (40%)
        val headAngleScore = when {
            abs(face.headEulerAngleY) <= 8.0 && abs(face.headEulerAngleZ) <= 6.0 -> 1.0f
            abs(face.headEulerAngleY) <= 15.0 && abs(face.headEulerAngleZ) <= 10.0 -> 0.7f
            else -> 0.3f
        }
        quality += headAngleScore * 0.4f
        
        // 眼睛睁开程度评分 (30%)
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0f
        val eyeScore = (leftEyeOpen + rightEyeOpen) / 2f
        quality += eyeScore * 0.3f
        
        // 人脸尺寸评分 (20%) - 提高尺寸要求，确保人脸足够大
        val faceSize = face.boundingBox.width() * face.boundingBox.height()
        val sizeScore = when {
            faceSize > 60000 -> 1.0f  // 更大的人脸尺寸要求
            faceSize > 45000 -> 0.8f  // 提高中等尺寸要求
            faceSize > 30000 -> 0.6f  // 提高最小可接受尺寸
            faceSize > 20000 -> 0.4f  // 新增较小尺寸档次
            else -> 0.2f              // 降低过小人脸的评分
        }
        quality += sizeScore * 0.2f
        
        // 微笑程度评分 (10%)
        val smileScore = face.smilingProbability ?: 0.5f
        quality += smileScore * 0.1f
        
        return quality.coerceIn(0f, 1f)
    }
    
    /**
     * 根据人脸质量生成提示信息
     * @param face 检测到的人脸
     * @return 提示信息
     */
    private fun getQualityHint(face: Face): String {
        return when {
            abs(face.headEulerAngleY) > 15.0 -> "请正对摄像头"
            abs(face.headEulerAngleZ) > 10.0 -> "请保持头部水平"
            (face.leftEyeOpenProbability ?: 0f) < 0.8f || 
            (face.rightEyeOpenProbability ?: 0f) < 0.8f -> "请睁开眼睛"
            face.boundingBox.width() * face.boundingBox.height() < 30000 -> "请靠近一些"
            else -> "请保持当前姿势"
        }
    }
    
    /**
     * 从图像中裁剪人脸区域
     * @param imageProxy 图像代理
     * @param boundingBox 人脸边界框
     * @return 裁剪后的人脸图片，失败返回null
     */
    private fun cropFaceFromImage(imageProxy: ImageProxy, boundingBox: Rect): Bitmap? {
        return try {
            // 使用更安全的图像转换方法
            val fullBitmap = convertImageProxyToBitmap(imageProxy) ?: return null
            
            // 应用图像旋转
            val rotatedBitmap = rotateBitmap(fullBitmap, imageProxy.imageInfo.rotationDegrees)
            
            // 智能扩展裁剪区域，包含更多面部信息 - 增大扩展比例确保完整人脸
            val expandRatio = 2.0f
            val newWidth = (boundingBox.width() * expandRatio).toInt()
            val newHeight = (boundingBox.height() * expandRatio).toInt()
            val centerX = boundingBox.centerX()
            val centerY = boundingBox.centerY()

            // 计算裁剪区域，确保不超出图像边界
            val newLeft = (centerX - newWidth / 2).coerceAtLeast(0)
            val newTop = (centerY - newHeight / 2).coerceAtLeast(0)
            val finalWidth = newWidth.coerceAtMost(rotatedBitmap.width - newLeft)
            val finalHeight = newHeight.coerceAtMost(rotatedBitmap.height - newTop)

            // 裁剪人脸区域
            val croppedBitmap = Bitmap.createBitmap(
                rotatedBitmap, newLeft, newTop, finalWidth, finalHeight
            )
            
            // 优化尺寸以节省内存 - 提高目标分辨率获得更清晰的照片
            val targetSize = 512
            val optimizedBitmap = if (croppedBitmap.width > targetSize || croppedBitmap.height > targetSize) {
                val scale = targetSize.toFloat() / maxOf(croppedBitmap.width, croppedBitmap.height)
                val scaledWidth = (croppedBitmap.width * scale).toInt()
                val scaledHeight = (croppedBitmap.height * scale).toInt()
                
                val scaledBitmap = Bitmap.createScaledBitmap(
                    croppedBitmap, scaledWidth, scaledHeight, true
                )
                
                // 回收原始裁剪图片
                if (scaledBitmap != croppedBitmap) {
                    croppedBitmap.recycle()
                }
                scaledBitmap
            } else {
                croppedBitmap
            }
            
            // 回收中间图片
            if (rotatedBitmap != fullBitmap && rotatedBitmap != optimizedBitmap) {
                rotatedBitmap.recycle()
            }
            if (fullBitmap != optimizedBitmap) {
                fullBitmap.recycle()
            }
            
            optimizedBitmap
        } catch (e: Exception) {
            logE("Error cropping face from image", tag = "FaceCaptureAnalyzer", throwable = e)
            null
        }
    }

    /**
     * 安全地将ImageProxy转换为Bitmap
     * 避免使用容易崩溃的toBitmap()方法
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val image = imageProxy.image
            if (image == null) {
                logW("Image is null", tag = "FaceCaptureAnalyzer")
                return null
            }
            
            // 再次检查图像状态
            try {
                val format = image.format
                logD("Converting image format: $format", tag = "FaceCaptureAnalyzer")
                
                when (format) {
                    ImageFormat.YUV_420_888 -> {
                        // 处理YUV格式图像
                        convertYuv420ToBitmap(image)
                    }
                    ImageFormat.JPEG -> {
                        // 处理JPEG格式图像
                        if (image.planes.isEmpty()) {
                            logW("JPEG image has no planes", tag = "FaceCaptureAnalyzer")
                            return null
                        }
                        
                        val plane = image.planes[0]
                        if (plane == null) {
                            logW("JPEG plane is null", tag = "FaceCaptureAnalyzer")
                            return null
                        }
                        
                        val buffer = plane.buffer
                        if (buffer == null) {
                            logW("JPEG buffer is null", tag = "FaceCaptureAnalyzer")
                            return null
                        }
                        
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    else -> {
                        // 对于其他格式，尝试使用官方方法，但增加异常处理
                        try {
                            imageProxy.toBitmap()
                        } catch (e: Exception) {
                            logW("Failed to convert image format $format", tag = "FaceCaptureAnalyzer", throwable = e)
                            null
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                logW("Image state error: ${e.message}", tag = "FaceCaptureAnalyzer")
                null
            }
        } catch (e: Exception) {
            logE("Error converting ImageProxy to Bitmap", tag = "FaceCaptureAnalyzer", throwable = e)
            null
        }
    }

    /**
     * 将YUV_420_888格式转换为Bitmap
     */
    private fun convertYuv420ToBitmap(image: android.media.Image): Bitmap? {
        return try {
            // 检查 planes 是否有效
            if (image.planes.size < 3) {
                logW("Invalid planes count: ${image.planes.size}", tag = "FaceCaptureAnalyzer")
                return null
            }
            
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            
            // 检查每个 plane 是否为 null
            if (yPlane == null || uPlane == null || vPlane == null) {
                logW("One or more planes are null", tag = "FaceCaptureAnalyzer")
                return null
            }
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer  
            val vBuffer = vPlane.buffer
            
            // 检查 buffer 是否为 null
            if (yBuffer == null || uBuffer == null || vBuffer == null) {
                logW("One or more buffers are null", tag = "FaceCaptureAnalyzer")
                return null
            }

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            // 检查 buffer 大小是否有效
            if (ySize <= 0 || uSize <= 0 || vSize <= 0) {
                logW("Invalid buffer sizes: Y=$ySize, U=$uSize, V=$vSize", tag = "FaceCaptureAnalyzer")
                return null
            }

            val nv21 = ByteArray(ySize + uSize + vSize)

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            logE("Error converting YUV to Bitmap", tag = "FaceCaptureAnalyzer", throwable = e)
            null
        }
    }

    /**
     * 旋转Bitmap以修正图像方向
     */
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        return if (rotationDegrees == 0) {
            bitmap
        } else {
            try {
                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
                rotatedBitmap
            } catch (e: Exception) {
                logE("Error rotating bitmap", tag = "FaceCaptureAnalyzer", throwable = e)
                bitmap
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        detector.close()
    }
}