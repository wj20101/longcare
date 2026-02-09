package com.ytone.longcare.features.face.detector

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ytone.longcare.features.face.ui.DetectedFace
import com.ytone.longcare.features.face.ui.FaceQualityResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * 静态图片人脸检测工具类
 * 复用 FaceCaptureAnalyzer 中的人脸检测和质量评估逻辑
 */
class StaticImageFaceDetector {
    
    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * 检测静态图片中的人脸
     */
    suspend fun detectFaces(bitmap: Bitmap): List<DetectedFace> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = suspendCancellableCoroutine<List<Face>> { continuation ->
                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        continuation.resume(faces)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            }
            
            faces.mapNotNull { face ->
                processFace(face, bitmap)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 处理单个人脸，提取人脸区域和质量评估
     */
    private fun processFace(face: Face, originalBitmap: Bitmap): DetectedFace? {
        return try {
            val boundingBox = face.boundingBox
            val croppedFace = cropFaceFromImage(originalBitmap, face)
            val quality = calculateFaceQuality(face, boundingBox, originalBitmap)
            
            DetectedFace(
                boundingBox = boundingBox,
                croppedFace = croppedFace,
                quality = quality,
                confidence = face.trackingId?.toFloat() ?: 0.8f
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从原图中裁剪人脸区域
     * 复用 FaceCaptureAnalyzer 中的 cropFaceFromImage 逻辑
     */
    private fun cropFaceFromImage(bitmap: Bitmap, face: Face): Bitmap {
        val boundingBox = face.boundingBox
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height

        // 扩展边界框以包含更多面部区域
        val expandRatio = 0.3f
        val expandX = (boundingBox.width() * expandRatio).toInt()
        val expandY = (boundingBox.height() * expandRatio).toInt()

        val left = max(0, boundingBox.left - expandX)
        val top = max(0, boundingBox.top - expandY)
        val right = min(imageWidth, boundingBox.right + expandX)
        val bottom = min(imageHeight, boundingBox.bottom + expandY)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid crop dimensions")
        }

        // 裁剪人脸区域
        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
        
        // 缩放到目标尺寸 (512x512)
        val targetSize = 512
        val scaledBitmap = croppedBitmap.scale(targetSize, targetSize, true)
        if (scaledBitmap != croppedBitmap) {
            croppedBitmap.recycle()
        }
        return scaledBitmap
    }

    /**
     * 计算人脸质量
     * 复用 FaceCaptureAnalyzer 中的质量评估逻辑
     */
    private fun calculateFaceQuality(face: Face, boundingBox: Rect, bitmap: Bitmap): Float {
        val imageArea = bitmap.width * bitmap.height
        val faceArea = boundingBox.width() * boundingBox.height()
        val faceRatio = faceArea.toFloat() / imageArea.toFloat()

        // 基础质量分数 (基于人脸大小)
        val sizeScore = when {
            faceRatio > 0.15f -> 1.0f
            faceRatio > 0.10f -> 0.8f
            faceRatio > 0.05f -> 0.6f
            else -> 0.3f
        }

        // 微笑概率加分
        val smileScore = face.smilingProbability?.let { smileProb ->
            if (smileProb > 0.3f) 0.1f else 0.0f
        } ?: 0.0f

        // 眼睛睁开概率加分
        val eyeScore = listOfNotNull(
            face.leftEyeOpenProbability,
            face.rightEyeOpenProbability
        ).let { eyeProbs ->
            if (eyeProbs.isNotEmpty() && eyeProbs.all { it > 0.5f }) 0.1f else 0.0f
        }

        return (sizeScore + smileScore + eyeScore).coerceIn(0.0f, 1.0f)
    }

    /**
     * 评估人脸质量并生成提示
     */
    fun evaluateFaceQuality(face: DetectedFace, bitmap: Bitmap): FaceQualityResult {
        val quality = face.quality
        val isGoodQuality = quality >= 0.7f
        
        val hints = mutableListOf<String>()
        
        when {
            quality < 0.3f -> hints.add("人脸质量较差，请重新拍照")
            quality < 0.5f -> hints.add("人脸质量一般，建议重新拍照")
            quality < 0.7f -> hints.add("人脸质量良好")
            else -> hints.add("人脸质量优秀")
        }

        // 检查人脸大小
        val imageArea = bitmap.width * bitmap.height
        val faceArea = face.boundingBox.width() * face.boundingBox.height()
        val faceRatio = faceArea.toFloat() / imageArea.toFloat()
        
        when {
            faceRatio < 0.05f -> hints.add("人脸太小，请靠近一些")
            faceRatio > 0.4f -> hints.add("人脸太大，请稍微远离")
        }

        return FaceQualityResult(
            quality = quality,
            isGoodQuality = isGoodQuality,
            hints = hints
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        faceDetector.close()
    }
}
