package com.ytone.longcare.features.identification.facecheck

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.features.identification.domain.CheckFaceFailure
import com.ytone.longcare.features.identification.domain.CheckFaceResult
import com.ytone.longcare.features.identification.domain.CheckFaceUseCase
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class FaceImageMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val byteCount: Int,
)

@HiltViewModel
class DefaultFaceVerificationViewModel @Inject constructor(
    private val imageEncoder: FaceImageEncoder,
    private val checkFaceUseCase: CheckFaceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DefaultFaceVerificationUiState>(
        DefaultFaceVerificationUiState.Capturing(),
    )
    val uiState: StateFlow<DefaultFaceVerificationUiState> = _uiState.asStateFlow()

    private val photoMetricsChannel = Channel<FaceImageMetrics>(capacity = Channel.BUFFERED)
    val photoMetrics = photoMetricsChannel.receiveAsFlow()

    private var captureAttempt = 0

    fun verifyFace(
        orderKey: OrderKey,
        bitmap: Bitmap,
    ) {
        if (
            _uiState.value is DefaultFaceVerificationUiState.ProcessingImage ||
            _uiState.value is DefaultFaceVerificationUiState.Verifying
        ) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = DefaultFaceVerificationUiState.ProcessingImage
                val encodedImage = imageEncoder.encode(bitmap)
                photoMetricsChannel.send(
                    FaceImageMetrics(
                        widthPx = encodedImage.widthPx,
                        heightPx = encodedImage.heightPx,
                        byteCount = encodedImage.byteCount,
                    ),
                )
                _uiState.value = DefaultFaceVerificationUiState.Verifying

                _uiState.value = when (
                    val result = checkFaceUseCase.execute(
                        orderId = orderKey.orderId,
                        faceImageBase64 = encodedImage.base64,
                    )
                ) {
                    CheckFaceResult.Success -> DefaultFaceVerificationUiState.Success
                    is CheckFaceResult.Error -> result.failure.toUiState()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DiagnosticEventTracker.trackError(
                    category = "default_face_verification",
                    event = "face_image_processing_failure",
                    description = "默认人脸验证图片处理失败",
                    throwable = error,
                    extras = mapOf(
                        "orderId" to orderKey.orderId,
                        "imageWidth" to bitmap.width,
                        "imageHeight" to bitmap.height,
                    ),
                )
                _uiState.value = DefaultFaceVerificationUiState.RetryableError()
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    fun retryCapture() {
        captureAttempt += 1
        _uiState.value = DefaultFaceVerificationUiState.Capturing(attempt = captureAttempt)
    }

    private fun CheckFaceFailure.toUiState(): DefaultFaceVerificationUiState = when (this) {
        CheckFaceFailure.UnsupportedOrder,
        CheckFaceFailure.MissingRegisteredFace,
        -> DefaultFaceVerificationUiState.TerminalError(this)

        CheckFaceFailure.SessionInvalidated -> DefaultFaceVerificationUiState.SessionInvalidated

        CheckFaceFailure.MissingImage,
        is CheckFaceFailure.Rejected,
        CheckFaceFailure.NetworkError,
        -> DefaultFaceVerificationUiState.RetryableError(this)
    }
}
