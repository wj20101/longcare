package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 回调函数类型定义
 */
typealias FaceCaptureCallback = (bitmap: Bitmap, quality: Float) -> Unit
typealias HintCallback = (hint: String) -> Unit
typealias FaceDetectionCallback = (snapshot: FaceDetectionSnapshot) -> Unit

data class FaceDetectionSnapshot(
    val detected: Boolean,
    val quality: Float,
    val confirmationProgress: Float,
)

/**
 * 人脸捕获图像分析器
 * 负责相机帧编排、ML Kit检测回调与捕获时序控制。
 */
class FaceCaptureAnalyzer(
    private val onFaceCaptured: FaceCaptureCallback,
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
    private val stabilityGate = FaceCaptureStabilityGate()
    private val imageExtractor = FaceImageExtractor()

    private val frameCount = AtomicInteger(0)
    private val frameSkip = 3
    private val isProcessing = AtomicBoolean(false)
    private val captureDispatched = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)
    private val frameLease = SingleFrameLease<ImageProxy>(::closeImageProxy)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isReleased.get() || isProcessing.get()) {
            closeImageProxy(imageProxy)
            return
        }

        val currentFrame = frameCount.incrementAndGet()
        if (currentFrame % frameSkip != 0) {
            closeImageProxy(imageProxy)
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            closeImageProxy(imageProxy)
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            closeImageProxy(imageProxy)
            return
        }
        if (!frameLease.acquire(imageProxy)) {
            isProcessing.set(false)
            closeImageProxy(imageProxy)
            return
        }

        try {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (isReleased.get()) {
                        completeFrame(imageProxy)
                        return@addOnSuccessListener
                    }

                    val processingJob = coroutineScope.launch(Dispatchers.Default) {
                        try {
                            processFaces(faces, imageProxy)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (!isReleased.get()) {
                                logE(
                                    "Error processing faces",
                                    tag = "FaceCaptureAnalyzer",
                                    throwable = e,
                                )
                                DiagnosticEventTracker.trackError(
                                    category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                                    event = "camera_frame_process_exception",
                                    description = "人脸采集相机帧处理异常",
                                    throwable = e,
                                    extras = imageProxy.diagnosticExtras("process_faces"),
                                )
                                onHintChanged("人脸图像处理失败，请重试")
                                resetDetectionState()
                            }
                        }
                    }
                    // invokeOnCompletion also runs when launch returns an already-cancelled Job,
                    // which closes the frame even if the coroutine body never starts.
                    processingJob.invokeOnCompletion { completeFrame(imageProxy) }
                }
                .addOnFailureListener { exception ->
                    if (isReleased.get()) {
                        completeFrame(imageProxy)
                    } else {
                        failAndCompleteFrame(imageProxy, exception)
                    }
                }
                .addOnCanceledListener {
                    completeFrame(imageProxy)
                }
        } catch (exception: Exception) {
            if (isReleased.get()) {
                completeFrame(imageProxy)
            } else {
                failAndCompleteFrame(imageProxy, exception)
            }
        }
    }

    private suspend fun processFaces(faces: List<Face>, imageProxy: ImageProxy) {
        if (isReleased.get()) return

        if (faces.isEmpty()) {
            resetDetectionState()
            onHintChanged("未检测到人脸，请调整位置")
            return
        }

        val bestFace = faces.maxByOrNull { qualityEvaluator.calculate(it) }
        bestFace?.let { face ->
            val quality = qualityEvaluator.calculate(face)
            val stability = stabilityGate.evaluate(
                quality = quality,
                timestampMillis = SystemClock.elapsedRealtime(),
            )
            onFaceDetectionChanged(
                FaceDetectionSnapshot(
                    detected = true,
                    quality = quality,
                    confirmationProgress = stability.confirmationProgress,
                ),
            )

            when {
                stability.isReadyToCapture && captureDispatched.compareAndSet(false, true) -> {
                    onHintChanged("人脸确认完成，正在采集...")

                    imageExtractor.cropFaceFromImage(imageProxy, face.boundingBox)?.let { bitmap ->
                        if (isReleased.get()) {
                            bitmap.recycle()
                        } else {
                            onFaceCaptured(bitmap, quality)
                            onHintChanged("人脸采集成功")
                        }
                    } ?: run {
                        captureDispatched.set(false)
                        stabilityGate.reset()
                        onFaceDetectionChanged(
                            FaceDetectionSnapshot(
                                detected = true,
                                quality = quality,
                                confirmationProgress = 0f,
                            ),
                        )
                        DiagnosticEventTracker.trackError(
                            category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                            event = "camera_frame_crop_empty",
                            description = "人脸采集裁剪结果为空",
                            extras = imageProxy.diagnosticExtras("crop_face") + mapOf(
                                "faceWidth" to face.boundingBox.width(),
                                "faceHeight" to face.boundingBox.height(),
                                "quality" to quality,
                            ),
                        )
                        onHintChanged("人脸图像处理失败，请重试")
                    }
                }

                stability.isQualified -> {
                    onHintChanged("请保持不动，正在确认人脸")
                }

                else -> {
                    onHintChanged(qualityEvaluator.getHint(face))
                }
            }
        } ?: run {
            resetDetectionState()
            DiagnosticEventTracker.trackError(
                category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                event = "camera_frame_best_face_missing",
                description = "人脸采集未选出最佳人脸",
                extras = imageProxy.diagnosticExtras("select_best_face") + mapOf(
                    "detectedFaceCount" to faces.size,
                ),
            )
            onHintChanged("人脸检测异常，请重试")
        }
    }

    fun reset() {
        frameCount.set(0)
        stabilityGate.reset()
        captureDispatched.set(false)
    }

    fun release() {
        if (!isReleased.compareAndSet(false, true)) return

        reset()
        // ML Kit or the crop coroutine can still be reading the active ImageProxy. Stop accepting
        // new frames now, but let the active owner close its frame before closing the detector.
        frameLease.stopAcceptingFrames(onDrained = detector::close)
    }

    private fun handleDetectionFailure(
        imageProxy: ImageProxy,
        exception: Exception,
    ) {
        logE("Face detection failed", tag = "FaceCaptureAnalyzer", throwable = exception)
        DiagnosticEventTracker.trackError(
            category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
            event = "camera_frame_detect_failure",
            description = "人脸采集相机帧检测失败",
            throwable = exception,
            extras = imageProxy.diagnosticExtras("mlkit_detect"),
        )
        onHintChanged("检测失败，请重试")
        resetDetectionState()
    }

    private fun failAndCompleteFrame(
        imageProxy: ImageProxy,
        exception: Exception,
    ) {
        try {
            handleDetectionFailure(imageProxy, exception)
        } finally {
            completeFrame(imageProxy)
        }
    }

    private fun completeFrame(imageProxy: ImageProxy) {
        frameLease.close(imageProxy)
        isProcessing.set(false)
    }

    private fun closeImageProxy(imageProxy: ImageProxy) {
        try {
            imageProxy.close()
        } catch (exception: Exception) {
            logW("Error closing ImageProxy", tag = "FaceCaptureAnalyzer", throwable = exception)
        }
    }

    private fun resetDetectionState() {
        stabilityGate.reset()
        onFaceDetectionChanged(
            FaceDetectionSnapshot(
                detected = false,
                quality = 0f,
                confirmationProgress = 0f,
            ),
        )
    }

    private fun ImageProxy.diagnosticExtras(stage: String): Map<String, Any?> =
        mapOf(
            "stage" to stage,
            "imageWidth" to width,
            "imageHeight" to height,
            "rotationDegrees" to imageInfo.rotationDegrees,
            "frameCount" to frameCount.get(),
        )

    private companion object {
        const val FACE_CAPTURE_DIAGNOSTIC_CATEGORY = "face_capture"
    }
}

/**
 * Holds at most one analyzer frame and guarantees that its close action runs once.
 *
 * Releasing the analyzer only stops new acquisitions. The active frame remains valid until its
 * detector/crop owner completes, so teardown cannot close an ImageProxy while it is being read.
 */
internal class SingleFrameLease<T : Any>(
    private val closeAction: (T) -> Unit,
) {
    private val lock = Any()
    private var activeFrame: T? = null
    private var acceptsFrames = true
    private var onDrained: (() -> Unit)? = null

    fun acquire(frame: T): Boolean = synchronized(lock) {
        if (!acceptsFrames || activeFrame != null) {
            false
        } else {
            activeFrame = frame
            true
        }
    }

    fun close(frame: T) {
        var drainAction: (() -> Unit)? = null
        val shouldClose = synchronized(lock) {
            if (activeFrame === frame) {
                activeFrame = null
                if (!acceptsFrames) {
                    drainAction = onDrained
                    onDrained = null
                }
                true
            } else {
                false
            }
        }
        if (shouldClose) {
            try {
                closeAction(frame)
            } finally {
                drainAction?.invoke()
            }
        }
    }

    fun stopAcceptingFrames(onDrained: () -> Unit) {
        val isAlreadyDrained = synchronized(lock) {
            acceptsFrames = false
            if (activeFrame == null) {
                true
            } else {
                this.onDrained = onDrained
                false
            }
        }
        if (isAlreadyDrained) {
            onDrained()
        }
    }
}
