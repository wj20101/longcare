package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 回调函数类型定义
 */
typealias FaceCaptureCallback = (bitmap: Bitmap, quality: Float) -> Unit
typealias ProcessingStateCallback = (isProcessing: Boolean) -> Unit
typealias HintCallback = (hint: String) -> Unit
typealias FaceDetectionCallback = (detected: Boolean, quality: Float) -> Unit

/**
 * 人脸捕获图像分析器
 * 负责相机帧编排、ML Kit检测回调与捕获时序控制。
 */
class FaceCaptureAnalyzer(
    private val onFaceCaptured: FaceCaptureCallback,
    private val onProcessingStateChanged: ProcessingStateCallback,
    private val onHintChanged: HintCallback,
    private val onFaceDetectionChanged: FaceDetectionCallback,
    private val coroutineScope: CoroutineScope
) : ImageAnalysis.Analyzer {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.3f)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)
    private val qualityEvaluator = FaceCaptureQualityEvaluator()
    private val imageExtractor = FaceImageExtractor()

    private var frameCount = 0
    private val frameSkip = 3
    private val isProcessing = AtomicBoolean(false)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing.get()) {
            imageProxy.close()
            return
        }

        frameCount++
        if (frameCount % frameSkip != 0) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            if (!isProcessing.compareAndSet(false, true)) {
                imageProxy.close()
                return
            }
            onProcessingStateChanged(true)

            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(image)
                .addOnSuccessListener { faces ->
                    coroutineScope.launch(Dispatchers.Default) {
                        try {
                            processFaces(faces, imageProxy)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logE("Error processing faces", tag = "FaceCaptureAnalyzer", throwable = e)
                            onHintChanged("处理失败，请重试")
                            onFaceDetectionChanged(false, 0f)
                        } finally {
                            isProcessing.set(false)
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
                    isProcessing.set(false)
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

    private suspend fun processFaces(faces: List<Face>, imageProxy: ImageProxy) {
        if (faces.isEmpty()) {
            onFaceDetectionChanged(false, 0f)
            onHintChanged("未检测到人脸，请调整位置")
            return
        }

        val bestFace = faces.maxByOrNull { qualityEvaluator.calculate(it) }
        bestFace?.let { face ->
            val quality = qualityEvaluator.calculate(face)
            onFaceDetectionChanged(true, quality)

            if (quality > 0.8f) {
                onHintChanged("检测到高质量人脸，正在捕获...")

                imageExtractor.cropFaceFromImage(imageProxy, face.boundingBox)?.let { bitmap ->
                    onFaceCaptured(bitmap, quality)
                    onHintChanged("人脸捕获成功！")
                } ?: run {
                    onHintChanged("图像处理失败，请重试")
                }
            } else {
                onHintChanged(qualityEvaluator.getHint(face))
            }
        } ?: run {
            onFaceDetectionChanged(false, 0f)
            onHintChanged("人脸检测异常，请重试")
        }
    }

    fun release() {
        detector.close()
    }
}
