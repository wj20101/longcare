package com.ytone.longcare.features.facecapture

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.logE
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

typealias HintCallback = (hint: String) -> Unit
typealias FaceDetectionCallback = (snapshot: FaceDetectionSnapshot) -> Unit
typealias FaceCaptureRequestCallback = (quality: Float) -> Unit

data class FaceDetectionSnapshot(
    val detected: Boolean,
    val quality: Float,
    val confirmationProgress: Float,
)

/**
 * CameraX/ML Kit analyzer that verifies one complete blink before requesting a still capture.
 *
 * [MlKitAnalyzer] owns analysis frame closing and CameraX backpressure. The final photo is captured
 * separately through ImageCapture, so the app does not hold or manually crop an analysis frame.
 */
class FaceCaptureAnalyzer(
    callbackExecutor: Executor,
    private val onCaptureRequested: FaceCaptureRequestCallback,
    private val onHintChanged: HintCallback,
    private val onFaceDetectionChanged: FaceDetectionCallback,
) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.3f)
            .enableTracking()
            .build(),
    )
    private val qualityEvaluator = FaceCaptureQualityEvaluator()
    private val blinkGate = FaceBlinkGate()
    private val captureDispatched = AtomicBoolean(false)
    private val isDetectionEnabled = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)

    val imageAnalyzer: ImageAnalysis.Analyzer = MlKitAnalyzer(
        listOf(detector),
        ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
        callbackExecutor,
    ) { result ->
        if (!isReleased.get() && isDetectionEnabled.get()) {
            handleResult(result)
        }
    }

    private fun handleResult(result: MlKitAnalyzer.Result) {
        val detectionFailure = result.getThrowable(detector)
        if (detectionFailure != null) {
            handleDetectionFailure(detectionFailure)
            return
        }

        try {
            processFaces(result.getValue(detector).orEmpty())
        } catch (error: Exception) {
            handleDetectionFailure(error)
        }
    }

    private fun processFaces(faces: List<Face>) {
        when {
            faces.isEmpty() -> {
                resetDetectionState()
                onHintChanged("未检测到人脸，请将面部置于取景框内")
            }

            faces.size > 1 -> {
                resetDetectionState()
                onHintChanged("请确保取景框内只有一人")
            }

            else -> processSingleFace(faces.single())
        }
    }

    private fun processSingleFace(face: Face) {
        val positionQuality = qualityEvaluator.calculatePositionQuality(face)
        val positionHint = qualityEvaluator.getPositionHint(face)
        if (positionHint != null) {
            blinkGate.reset()
            publishDetection(
                detected = true,
                quality = positionQuality,
                progress = 0f,
            )
            onHintChanged(positionHint)
            return
        }

        if (face.trackingId == null) {
            blinkGate.reset()
            publishDetection(
                detected = true,
                quality = positionQuality,
                progress = 0f,
            )
            onHintChanged("请保持面部不动，正在确认人脸")
            return
        }

        val blinkResult = blinkGate.evaluate(
            FaceBlinkObservation(
                trackingId = face.trackingId,
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
                positionQualified = true,
                timestampMillis = SystemClock.elapsedRealtime(),
            ),
        )
        publishDetection(
            detected = true,
            quality = positionQuality,
            progress = blinkResult.progress,
        )

        if (
            blinkResult.isReadyToCapture &&
            captureDispatched.compareAndSet(false, true)
        ) {
            onHintChanged("眨眼验证完成，正在拍照…")
            onCaptureRequested(qualityEvaluator.calculate(face))
        } else {
            onHintChanged(blinkResult.stage.userHint)
        }
    }

    fun setDetectionEnabled(enabled: Boolean) {
        if (isReleased.get()) return

        val wasEnabled = isDetectionEnabled.getAndSet(enabled)
        when {
            enabled && !wasEnabled -> reset()
            !enabled && wasEnabled -> blinkGate.reset()
        }
    }

    private fun reset() {
        blinkGate.reset()
        captureDispatched.set(false)
        if (!isReleased.get() && isDetectionEnabled.get()) {
            resetDetectionState()
        }
    }

    fun release() {
        if (!isReleased.compareAndSet(false, true)) return
        blinkGate.reset()
        detector.close()
    }

    private fun handleDetectionFailure(error: Throwable) {
        if (!isDetectionEnabled.get() || isReleased.get()) return
        logE("Face detection failed", tag = "FaceCaptureAnalyzer", throwable = error)
        DiagnosticEventTracker.trackError(
            category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
            event = "camera_frame_detect_failure",
            description = "人脸采集相机帧检测失败",
            throwable = error,
        )
        resetDetectionState()
        onHintChanged("人脸检测失败，请重试")
    }

    private fun resetDetectionState() {
        blinkGate.reset()
        publishDetection(
            detected = false,
            quality = 0f,
            progress = 0f,
        )
    }

    private fun publishDetection(
        detected: Boolean,
        quality: Float,
        progress: Float,
    ) {
        if (!isDetectionEnabled.get() || isReleased.get()) return
        onFaceDetectionChanged(
            FaceDetectionSnapshot(
                detected = detected,
                quality = quality,
                confirmationProgress = progress,
            ),
        )
    }

    private val FaceBlinkStage.userHint: String
        get() = when (this) {
            FaceBlinkStage.WAITING_FOR_OPEN_EYES -> "请睁开双眼并正对摄像头"
            FaceBlinkStage.WAITING_FOR_BLINK -> "请自然眨一下眼睛"
            FaceBlinkStage.WAITING_FOR_REOPEN -> "请睁开双眼"
            FaceBlinkStage.VERIFYING_REOPEN -> "眨眼完成，请保持不动"
            FaceBlinkStage.COMPLETE -> "眨眼验证完成，正在拍照…"
        }

    private companion object {
        const val FACE_CAPTURE_DIAGNOSTIC_CATEGORY = "face_capture"
    }
}
