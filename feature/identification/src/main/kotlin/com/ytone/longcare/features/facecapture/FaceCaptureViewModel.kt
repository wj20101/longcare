package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@HiltViewModel
class FaceCaptureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(FaceCaptureUiState())
    val uiState: StateFlow<FaceCaptureUiState> = _uiState.asStateFlow()

    private val captureAccepted = AtomicBoolean(false)
    private val pendingFace = AtomicReference<Bitmap?>(null)
    private var preparationJob: Job? = null

    @Synchronized
    fun startPreparationCountdown() {
        preparationJob?.cancel()
        pendingFace.getAndSet(null)?.recycle()
        captureAccepted.set(false)
        _uiState.value = FaceCaptureUiState(
            phase = FaceCapturePhase.PREPARING,
            countdownSeconds = PREPARATION_COUNTDOWN_SECONDS,
            userHint = FaceCaptureHint.PREPARE,
        )

        preparationJob = viewModelScope.launch {
            for (seconds in PREPARATION_COUNTDOWN_SECONDS downTo 1) {
                _uiState.update { current ->
                    if (current.phase == FaceCapturePhase.PREPARING) {
                        current.copy(countdownSeconds = seconds)
                    } else {
                        current
                    }
                }
                delay(COUNTDOWN_TICK_MILLIS)
            }

            _uiState.update { current ->
                if (current.phase == FaceCapturePhase.PREPARING) {
                    current.copy(
                        phase = FaceCapturePhase.SCANNING,
                        countdownSeconds = 0,
                        userHint = FaceCaptureHint.OPEN_EYES_FACING_CAMERA,
                    )
                } else {
                    current
                }
            }
        }
    }

    @Synchronized
    fun onBlinkVerified() {
        val current = _uiState.value
        if (!current.isDetectionEnabled || captureAccepted.get()) return

        _uiState.value = current.copy(
            phase = FaceCapturePhase.CAPTURING,
            countdownSeconds = 0,
            userHint = FaceCaptureHint.BLINK_CAPTURED,
            faceDetected = true,
            facePositionQualified = true,
        )
    }

    @Synchronized
    fun onFaceCaptured(faceBitmap: Bitmap) {
        if (!_uiState.value.isStillCaptureRequested) {
            faceBitmap.recycle()
            return
        }

        if (!captureAccepted.compareAndSet(false, true)) {
            faceBitmap.recycle()
            return
        }

        pendingFace.set(faceBitmap)
        _uiState.update {
            it.copy(
                phase = FaceCapturePhase.CAPTURED,
                captureReady = true,
                countdownSeconds = 0,
                userHint = FaceCaptureHint.SUCCESS,
                faceDetected = true,
                facePositionQualified = true,
            )
        }
    }

    @Synchronized
    fun onStillCaptureFailed(hint: FaceCaptureHint) {
        if (!_uiState.value.isStillCaptureRequested || captureAccepted.get()) return

        _uiState.value = FaceCaptureUiState(
            phase = FaceCapturePhase.SCANNING,
            countdownSeconds = 0,
            userHint = hint,
        )
    }

    /** 将唯一一张相机采集图移交给验证流程，调用方接管 Bitmap 生命周期。 */
    @Synchronized
    fun takeCapturedFace(): Bitmap? {
        val bitmap = pendingFace.getAndSet(null) ?: return null
        _uiState.update { it.copy(captureReady = false) }
        return bitmap
    }

    fun updateFaceDetectionState(snapshot: FaceDetectionSnapshot) {
        _uiState.update { current ->
            if (!captureAccepted.get() && current.isDetectionEnabled) {
                current.copy(
                    phase = if (snapshot.isConfirmingBlink) {
                        FaceCapturePhase.CONFIRMING
                    } else {
                        FaceCapturePhase.SCANNING
                    },
                    faceDetected = snapshot.detected,
                    facePositionQualified = snapshot.positionQualified,
                    userHint = snapshot.hint,
                )
            } else {
                current
            }
        }
    }

    @Synchronized
    fun resetCapture() {
        preparationJob?.cancel()
        preparationJob = null
        pendingFace.getAndSet(null)?.recycle()
        captureAccepted.set(false)
        _uiState.value = FaceCaptureUiState()
    }

    @Synchronized
    override fun onCleared() {
        preparationJob?.cancel()
        pendingFace.getAndSet(null)?.recycle()
    }

    private companion object {
        const val COUNTDOWN_TICK_MILLIS = 1_000L
    }
}

private val FaceDetectionSnapshot.isConfirmingBlink: Boolean
    get() = hint in CONFIRMING_BLINK_HINTS

private val CONFIRMING_BLINK_HINTS = setOf(
    FaceCaptureHint.REOPEN_EYES,
    FaceCaptureHint.HOLD_AFTER_BLINK,
    FaceCaptureHint.BLINK_CAPTURED,
)
