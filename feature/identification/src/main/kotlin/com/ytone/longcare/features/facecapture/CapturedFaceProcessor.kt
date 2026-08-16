package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal typealias CapturedFaceCallback = (bitmap: Bitmap, quality: Float) -> Unit
internal typealias CapturedFaceFailureCallback = (message: String, error: Throwable?) -> Unit

/** Performs a final ML Kit check and crop on the high-quality CameraX still image. */
internal class CapturedFaceProcessor(
    private val callbackExecutor: Executor,
    private val onFaceProcessed: CapturedFaceCallback,
    private val onFailure: CapturedFaceFailureCallback,
) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.2f)
            .build(),
    )
    private val qualityEvaluator = FaceCaptureQualityEvaluator()
    private val imageExtractor = FaceImageExtractor()
    private val isProcessing = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)
    private val detectorClosed = AtomicBoolean(false)

    fun process(imageProxy: ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            closeImageProxy(imageProxy)
            return
        }
        if (isReleased.get()) {
            closeImageProxy(imageProxy)
            finishProcessing()
            return
        }

        val uprightBitmap = try {
            val source = imageProxyToBitmap(imageProxy)
                ?: return failBeforeDetection("人脸照片处理失败，请重新拍摄")
            rotateFaceBitmap(source, imageProxy.imageInfo.rotationDegrees)
        } catch (error: Exception) {
            failBeforeDetection("人脸照片处理失败，请重新拍摄", error)
            return
        } finally {
            closeImageProxy(imageProxy)
        }

        try {
            detector.process(InputImage.fromBitmap(uprightBitmap, 0))
                .addOnSuccessListener(callbackExecutor) { faces ->
                    if (isReleased.get()) return@addOnSuccessListener

                    try {
                        when {
                            faces.isEmpty() -> notifyFailure(
                                "拍摄照片中未检测到人脸，请重新拍摄",
                            )
                            faces.size > 1 -> notifyFailure(
                                "检测到多人，请确保取景框内只有一人",
                            )
                            else -> processDetectedFace(
                                source = uprightBitmap,
                                face = faces.single(),
                            )
                        }
                    } catch (error: Exception) {
                        notifyFailure("人脸照片处理失败，请重新拍摄", error)
                    }
                }
                .addOnFailureListener(callbackExecutor) { error ->
                    if (!isReleased.get()) {
                        notifyFailure("人脸照片检测失败，请重新拍摄", error)
                    }
                }
                .addOnCompleteListener(callbackExecutor) {
                    if (!uprightBitmap.isRecycled) {
                        uprightBitmap.recycle()
                    }
                    finishProcessing()
                }
        } catch (error: Exception) {
            if (!uprightBitmap.isRecycled) {
                uprightBitmap.recycle()
            }
            notifyFailure("人脸照片检测失败，请重新拍摄", error)
            finishProcessing()
        }
    }

    fun release() {
        if (!isReleased.compareAndSet(false, true)) return
        if (!isProcessing.get()) {
            closeDetector()
        }
    }

    private fun processDetectedFace(
        source: Bitmap,
        face: com.google.mlkit.vision.face.Face,
    ) {
        if (!qualityEvaluator.isCaptureReady(face)) {
            notifyFailure(qualityEvaluator.getCaptureHint(face))
            return
        }

        val croppedFace = imageExtractor.cropFaceFromBitmap(source, face.boundingBox)
        if (croppedFace == null) {
            notifyFailure("人脸照片处理失败，请重新拍摄")
            return
        }

        if (isReleased.get()) {
            croppedFace.recycle()
            return
        }

        try {
            onFaceProcessed(croppedFace, qualityEvaluator.calculate(face))
        } catch (error: Exception) {
            if (!croppedFace.isRecycled) {
                croppedFace.recycle()
            }
            notifyFailure("人脸照片处理失败，请重新拍摄", error)
        }
    }

    private fun failBeforeDetection(
        message: String,
        error: Throwable? = null,
    ) {
        if (!isReleased.get()) {
            notifyFailure(message, error)
        }
        finishProcessing()
    }

    private fun notifyFailure(
        message: String,
        error: Throwable? = null,
    ) {
        if (isReleased.get()) return
        if (error != null) {
            logE("Captured face processing failed", tag = TAG, throwable = error)
        }
        onFailure(message, error)
    }

    private fun finishProcessing() {
        isProcessing.set(false)
        if (isReleased.get()) {
            closeDetector()
        }
    }

    private fun closeDetector() {
        if (detectorClosed.compareAndSet(false, true)) {
            detector.close()
        }
    }

    private fun closeImageProxy(imageProxy: ImageProxy) {
        try {
            imageProxy.close()
        } catch (error: Exception) {
            logW("Error closing captured ImageProxy", tag = TAG, throwable = error)
        }
    }

    private companion object {
        const val TAG = "CapturedFaceProcessor"
    }
}
