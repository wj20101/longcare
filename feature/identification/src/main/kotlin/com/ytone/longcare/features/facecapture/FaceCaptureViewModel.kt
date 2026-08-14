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
            userHint = "请正对摄像头，保持面部光线充足",
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
                        userHint = "请正对摄像头，保持面部光线充足",
                    )
                } else {
                    current
                }
            }
        }
    }

    @Synchronized
    fun onFaceCaptured(faceBitmap: Bitmap, quality: Float) {
        if (!_uiState.value.isDetectionEnabled) {
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
                userHint = "人脸采集成功",
                faceDetected = true,
                faceQuality = quality,
                confirmationProgress = 1f,
            )
        }
    }

    /** 将唯一一张相机采集图移交给验证流程，调用方接管 Bitmap 生命周期。 */
    @Synchronized
    fun takeCapturedFace(): Bitmap? {
        val bitmap = pendingFace.getAndSet(null) ?: return null
        _uiState.update { it.copy(captureReady = false) }
        return bitmap
    }

    fun updateUserHint(hint: String) {
        _uiState.update { current ->
            if (!captureAccepted.get() && current.isDetectionEnabled) {
                current.copy(userHint = hint)
            } else {
                current
            }
        }
    }

    fun updateFaceDetectionState(snapshot: FaceDetectionSnapshot) {
        _uiState.update { current ->
            if (!captureAccepted.get() && current.isDetectionEnabled) {
                current.copy(
                    phase = if (snapshot.confirmationProgress > 0f) {
                        FaceCapturePhase.CONFIRMING
                    } else {
                        FaceCapturePhase.SCANNING
                    },
                    faceDetected = snapshot.detected,
                    faceQuality = snapshot.quality,
                    confirmationProgress = snapshot.confirmationProgress,
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
