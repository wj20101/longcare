package com.ytone.longcare.features.facecapture

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

typealias FaceCaptureRequestCallback = () -> Unit

/**
 * CameraX/ML Kit analyzer that verifies one complete blink before requesting a still capture.
 *
 * [MlKitAnalyzer] owns analysis frame closing and CameraX backpressure. The final photo is captured
 * separately through ImageCapture, so the app does not hold or manually crop an analysis frame.
 */
class FaceCaptureAnalyzer(
    callbackExecutor: Executor,
    private val onCaptureRequested: FaceCaptureRequestCallback,
    onFaceDetectionChanged: FaceDetectionCallback,
) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.2f)
            .build(),
    )
    private val qualityEvaluator = FaceCaptureQualityEvaluator()
    private val blinkGate = FaceBlinkGate()
    private val statePublisher = FaceDetectionStatePublisher(onFaceDetectionChanged)
    private val captureDispatched = AtomicBoolean(false)
    private val isDetectionEnabled = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)
    private var lastDiagnosticState: String? = null

    val imageAnalyzer: ImageAnalysis.Analyzer = FrameRateLimitedAnalyzer(
        delegate = MlKitAnalyzer(
            listOf(detector),
            ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
            callbackExecutor,
        ) { result ->
            if (!isReleased.get() && isDetectionEnabled.get()) {
                handleResult(result)
            }
        },
        targetFramesPerSecond = FACE_ANALYSIS_FRAMES_PER_SECOND,
    )

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
                logDetectionState("no_face")
                resetDetectionState(FaceCaptureHint.NO_FACE)
            }

            faces.size > 1 -> {
                logDetectionState("multiple_faces")
                resetDetectionState(FaceCaptureHint.SINGLE_PERSON)
            }

            else -> processSingleFace(faces.single())
        }
    }

    private fun processSingleFace(face: Face) {
        val positionHint = qualityEvaluator.getPositionHint(face)
        if (positionHint != null) {
            logDetectionState("position:${positionHint.name}")
            blinkGate.reset()
            publishDetection(
                detected = true,
                positionQualified = false,
                progress = 0f,
                hint = positionHint,
            )
            return
        }

        val blinkResult = blinkGate.evaluate(
            FaceBlinkObservation(
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
                positionQualified = true,
                timestampMillis = SystemClock.elapsedRealtime(),
            ),
        )
        logBlinkState(face, blinkResult)
        publishDetection(
            detected = true,
            positionQualified = true,
            progress = blinkResult.progress,
            hint = blinkResult.stage.userHint,
        )

        if (
            blinkResult.isReadyToCapture &&
            captureDispatched.compareAndSet(false, true)
        ) {
            onCaptureRequested()
        }
    }

    fun setDetectionEnabled(enabled: Boolean) {
        if (isReleased.get()) return

        val wasEnabled = isDetectionEnabled.getAndSet(enabled)
        when {
            enabled && !wasEnabled -> {
                lastDiagnosticState = null
                statePublisher.reset()
                reset()
            }
            !enabled && wasEnabled -> {
                blinkGate.reset()
                statePublisher.reset()
            }
        }
    }

    private fun reset() {
        blinkGate.reset()
        captureDispatched.set(false)
        if (!isReleased.get() && isDetectionEnabled.get()) {
            resetDetectionState(FaceCaptureHint.OPEN_EYES_FACING_CAMERA)
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
        resetDetectionState(FaceCaptureHint.DETECTION_FAILED)
    }

    private fun resetDetectionState(hint: FaceCaptureHint) {
        blinkGate.reset()
        publishDetection(
            detected = false,
            positionQualified = false,
            progress = 0f,
            hint = hint,
        )
    }

    private fun publishDetection(
        detected: Boolean,
        positionQualified: Boolean,
        progress: Float,
        hint: FaceCaptureHint,
    ) {
        if (!isDetectionEnabled.get() || isReleased.get()) return
        statePublisher.publish(
            detected = detected,
            positionQualified = positionQualified,
            progress = progress,
            hint = hint,
            timestampMillis = SystemClock.elapsedRealtime(),
        )
    }

    private fun logBlinkState(face: Face, result: FaceBlinkResult) {
        val state = "blink:${result.stage.name}"
        if (state == lastDiagnosticState) return

        lastDiagnosticState = state
        logD(
            message = buildString {
                append("state=")
                append(result.stage.name)
                append(", leftEyeOpen=")
                append(face.leftEyeOpenProbability)
                append(", rightEyeOpen=")
                append(face.rightEyeOpenProbability)
                append(", progress=")
                append(result.progress)
            },
            tag = "FaceCaptureAnalyzer",
        )
    }

    private fun logDetectionState(state: String) {
        if (state == lastDiagnosticState) return

        lastDiagnosticState = state
        logD("state=$state", tag = "FaceCaptureAnalyzer")
    }

    private val FaceBlinkStage.userHint: FaceCaptureHint
        get() = when (this) {
            FaceBlinkStage.WAITING_FOR_OPEN_EYES -> FaceCaptureHint.OPEN_EYES_FACING_CAMERA
            FaceBlinkStage.WAITING_FOR_BLINK -> FaceCaptureHint.BLINK
            FaceBlinkStage.WAITING_FOR_REOPEN -> FaceCaptureHint.REOPEN_EYES
            FaceBlinkStage.VERIFYING_REOPEN -> FaceCaptureHint.HOLD_AFTER_BLINK
            FaceBlinkStage.COMPLETE -> FaceCaptureHint.BLINK_CAPTURED
        }

    private companion object {
        const val FACE_CAPTURE_DIAGNOSTIC_CATEGORY = "face_capture"
        const val FACE_ANALYSIS_FRAMES_PER_SECOND = 20
    }
}
