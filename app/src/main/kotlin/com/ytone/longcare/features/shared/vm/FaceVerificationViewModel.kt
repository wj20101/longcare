package com.ytone.longcare.features.shared.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.shared.FaceVerificationPhotoProcessor
import com.ytone.longcare.features.shared.ProcessedFacePhoto
import com.ytone.longcare.features.shared.resolveFaceCaptureErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FaceVerificationViewModel @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    private val photoProcessor: FaceVerificationPhotoProcessor,
) : ViewModel() {

    sealed class FaceVerifyUiState {
        object Idle : FaceVerifyUiState()
        object Initializing : FaceVerifyUiState()
        object Verifying : FaceVerifyUiState()
        data class Success(val result: FaceVerifyResult) : FaceVerifyUiState()
        data class Error(val error: FaceVerifyError?, val message: String) : FaceVerifyUiState()
        object Cancelled : FaceVerifyUiState()
    }

    sealed interface PhotoProcessingState {
        data object Idle : PhotoProcessingState
        data object Processing : PhotoProcessingState
        data class Success(val photo: ProcessedFacePhoto) : PhotoProcessingState
        data class Error(val message: String) : PhotoProcessingState
    }

    private val _uiState = MutableStateFlow<FaceVerifyUiState>(FaceVerifyUiState.Idle)
    val uiState: StateFlow<FaceVerifyUiState> = _uiState.asStateFlow()

    private val _sdkLaunchRequest = MutableStateFlow<SharedFaceSdkLaunchRequest?>(null)
    val sdkLaunchRequest: StateFlow<SharedFaceSdkLaunchRequest?> = _sdkLaunchRequest.asStateFlow()
    private val _photoProcessingState =
        MutableStateFlow<PhotoProcessingState>(PhotoProcessingState.Idle)
    val photoProcessingState: StateFlow<PhotoProcessingState> =
        _photoProcessingState.asStateFlow()
    private var activeLaunchRequest: SharedFaceSdkLaunchRequest? = null
    private var photoProcessingJob: Job? = null
    private var latestPhotoProcessingId = 0L
    private var nextLaunchId = 0L
    private var latestPreparationId = 0L

    fun startFaceVerificationWithAutoSign(
        name: String,
        idNo: String,
        orderNo: String,
        userId: String
    ) {
        prepareFaceVerification(
            FaceVerificationRequest(name = name, idNo = idNo, orderNo = orderNo, userId = userId)
        )
    }

    fun startFaceVerificationWithAutoSign(
        orderNo: String,
        userId: String,
        sourcePhotoStr: String
    ) {
        prepareFaceVerification(
            FaceVerificationRequest(
                name = null,
                idNo = null,
                orderNo = orderNo,
                userId = userId,
                sourcePhotoStr = sourcePhotoStr
            )
        )
    }

    fun resetState() {
        latestPreparationId++
        clearPhotoProcessingState()
        _uiState.value = FaceVerifyUiState.Idle
        _sdkLaunchRequest.value = null
        activeLaunchRequest = null
    }

    fun processCapturedPhoto(imagePath: String) {
        val processingId = ++latestPhotoProcessingId
        photoProcessingJob?.cancel()
        _photoProcessingState.value = PhotoProcessingState.Processing
        photoProcessingJob =
            viewModelScope.launch {
                try {
                    val processedPhoto = photoProcessor.process(imagePath)
                    if (processingId == latestPhotoProcessingId) {
                        _photoProcessingState.value =
                            PhotoProcessingState.Success(processedPhoto)
                    }
                } catch (exception: CancellationException) {
                    if (processingId == latestPhotoProcessingId) {
                        _photoProcessingState.value = PhotoProcessingState.Idle
                    }
                    throw exception
                } catch (exception: Exception) {
                    if (processingId == latestPhotoProcessingId) {
                        _photoProcessingState.value =
                            PhotoProcessingState.Error(resolveFaceCaptureErrorMessage(exception))
                    }
                }
            }
    }

    fun clearPhotoProcessingState() {
        latestPhotoProcessingId++
        photoProcessingJob?.cancel()
        photoProcessingJob = null
        _photoProcessingState.value = PhotoProcessingState.Idle
    }

    fun clearError() {
        if (_uiState.value is FaceVerifyUiState.Error) {
            _uiState.value = FaceVerifyUiState.Idle
        }
    }

    fun consumeSdkLaunchRequest(id: Long) {
        if (_sdkLaunchRequest.value?.id == id) {
            _sdkLaunchRequest.value = null
        }
    }

    fun onFaceSdkEvent(launchId: Long, event: FaceSdkEvent) {
        val launch = activeLaunchRequest?.takeIf { it.id == launchId } ?: return
        when (event) {
            FaceSdkEvent.InitSuccess -> _uiState.value = FaceVerifyUiState.Verifying
            is FaceSdkEvent.InitFailed -> emitError(
                message = "人脸验证初始化失败：${event.error.readableDescription()}",
                error = event.error,
                event = "shared_face_init_failed",
                description = "共享人脸验证初始化失败",
                extras = launch.request.diagnosticExtras("init_failed"),
            )
            is FaceSdkEvent.VerifySuccess -> _uiState.value = FaceVerifyUiState.Success(event.result)
            is FaceSdkEvent.VerifyFailed -> emitError(
                message = "人脸验证失败：${event.error.readableDescription()}",
                error = event.error,
                event = "shared_face_verify_failed",
                description = "共享人脸验证失败",
                extras = launch.request.diagnosticExtras("verify_failed"),
            )
            FaceSdkEvent.Cancelled -> {
                DiagnosticEventTracker.trackEvent(
                    category = DIAGNOSTIC_CATEGORY,
                    event = "shared_face_cancelled",
                    description = "共享人脸验证取消",
                    extras = launch.request.diagnosticExtras("cancelled"),
                )
                _uiState.value = FaceVerifyUiState.Cancelled
            }
        }
        if (event !is FaceSdkEvent.InitSuccess) {
            activeLaunchRequest = null
        }
    }

    private fun prepareFaceVerification(request: FaceVerificationRequest) {
        val preparationId = ++latestPreparationId
        _sdkLaunchRequest.value = null
        activeLaunchRequest = null
        _uiState.value = FaceVerifyUiState.Initializing
        viewModelScope.launch {
            val config = systemConfigManager.getFaceVerificationConfig()
            if (preparationId != latestPreparationId) return@launch
            if (config == null) {
                emitError(
                    message = "人脸验证配置不可用，请重新登录后重试",
                    error = null,
                    event = "shared_face_config_missing",
                    description = "共享人脸验证配置缺失",
                    extras = request.diagnosticExtras("config_missing"),
                )
                return@launch
            }
            val launchRequest = SharedFaceSdkLaunchRequest(
                id = ++nextLaunchId,
                config = config,
                request = request,
            )
            activeLaunchRequest = launchRequest
            _sdkLaunchRequest.value = launchRequest
        }
    }

    private fun emitError(
        message: String,
        error: FaceVerifyError?,
        event: String,
        description: String,
        extras: Map<String, Any?>,
    ) {
        DiagnosticEventTracker.trackError(
            category = DIAGNOSTIC_CATEGORY,
            event = event,
            description = description,
            extras = error.diagnosticExtras() + extras + mapOf("message" to message),
        )
        _uiState.value = FaceVerifyUiState.Error(error = error, message = message)
    }

    private fun FaceVerificationRequest.diagnosticExtras(stage: String): Map<String, Any?> =
        mapOf(
            "stage" to stage,
            "orderNoLength" to orderNo.length,
            "userIdLength" to userId.length,
            "usesSourcePhoto" to !sourcePhotoStr.isNullOrBlank(),
            "sourcePhotoLength" to (sourcePhotoStr?.length ?: 0),
            "usesIdentityFields" to (!name.isNullOrBlank() || !idNo.isNullOrBlank()),
        )

    private fun FaceVerifyError?.diagnosticExtras(): Map<String, Any?> {
        if (this == null) return emptyMap()
        return mapOf(
            "errorDomain" to domain,
            "errorCode" to code,
            "errorDescription" to description,
            "errorReason" to reason,
        )
    }

    private fun FaceVerifyError?.readableDescription(): String {
        if (this == null) return "请稍后重试"
        return description
            ?.takeIf { it.isNotBlank() }
            ?: reason?.takeIf { it.isNotBlank() }
            ?: "请稍后重试"
    }

    private companion object {
        const val DIAGNOSTIC_CATEGORY = "face_verification"
    }
}

data class SharedFaceSdkLaunchRequest(
    val id: Long,
    val config: FaceVerificationConfig,
    val request: FaceVerificationRequest,
)
