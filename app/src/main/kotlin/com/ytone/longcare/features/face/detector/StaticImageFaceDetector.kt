package com.ytone.longcare.features.face.detector

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ytone.longcare.features.face.ui.DetectedFace
import com.ytone.longcare.features.face.ui.FaceQualityResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 静态图片人脸检测工具类
 * 复用 FaceCaptureAnalyzer 中的人脸检测和质量评估逻辑
 */
class StaticImageFaceDetector {
    
    private val faceDetectorLazy = lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    private val faceDetector: FaceDetector by faceDetectorLazy

    /**
     * 检测静态图片中的人脸
     */
    suspend fun detectFaces(bitmap: Bitmap): List<DetectedFace> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = suspendCancellableCoroutine<List<Face>> { continuation ->
                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        if (continuation.isActive) {
                            continuation.resume(faces)
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }
            }
            
            faces.mapNotNull { face ->
                processFace(face, bitmap)
            }
        } catch (e: CancellationException) {
            throw e
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
     * 评估人脸质量并生成提示
     */
    fun evaluateFaceQuality(face: DetectedFace, bitmap: Bitmap): FaceQualityResult {
        val quality = face.quality
        val isGoodQuality = quality >= 0.7f

        // 检查人脸大小
        val imageArea = bitmap.width * bitmap.height
        val faceArea = face.boundingBox.width() * face.boundingBox.height()
        val faceRatio = faceArea.toFloat() / imageArea.toFloat()

        return FaceQualityResult(
            quality = quality,
            isGoodQuality = isGoodQuality,
            hints = buildFaceQualityHints(quality, faceRatio)
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        if (faceDetectorLazy.isInitialized()) {
            faceDetector.close()
        }
    }
}
