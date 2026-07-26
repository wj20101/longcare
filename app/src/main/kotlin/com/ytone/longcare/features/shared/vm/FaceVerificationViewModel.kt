package com.ytone.longcare.features.shared.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FaceVerificationViewModel @Inject constructor(
    private val faceVerifier: FaceVerifier,
    private val systemConfigManager: SystemConfigManager
) : ViewModel() {

    sealed interface FaceVerifyEvent {
        data class Success(val result: FaceVerifyResult) : FaceVerifyEvent
        data class Error(val message: String) : FaceVerifyEvent
        data object Cancelled : FaceVerifyEvent
    }

    sealed class FaceVerifyUiState {
        object Idle : FaceVerifyUiState()
        object Initializing : FaceVerifyUiState()
        object Verifying : FaceVerifyUiState()
        data class Success(val result: FaceVerifyResult) : FaceVerifyUiState()
        data class Error(val error: FaceVerifyError?, val message: String) : FaceVerifyUiState()
        object Cancelled : FaceVerifyUiState()
    }

    private val _uiState = MutableStateFlow<FaceVerifyUiState>(FaceVerifyUiState.Idle)
    val uiState: StateFlow<FaceVerifyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FaceVerifyEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<FaceVerifyEvent> = _events.asSharedFlow()

    fun startFaceVerificationWithAutoSign(
        context: Context,
        name: String,
        idNo: String,
        orderNo: String,
        userId: String
    ) {
        launchFaceVerification(
            context,
            FaceVerificationRequest(name = name, idNo = idNo, orderNo = orderNo, userId = userId)
        )
    }

    fun startFaceVerificationWithAutoSign(
        context: Context,
        orderNo: String,
        userId: String,
        sourcePhotoStr: String
    ) {
        launchFaceVerification(
            context,
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
        _uiState.value = FaceVerifyUiState.Idle
    }

    fun clearError() {
        if (_uiState.value is FaceVerifyUiState.Error) {
            _uiState.value = FaceVerifyUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceVerifier.release()
    }

    private fun launchFaceVerification(context: Context, request: FaceVerificationRequest) {
        viewModelScope.launch {
            _uiState.value = FaceVerifyUiState.Initializing
            startFaceVerificationInternal(context, request)
        }
    }

    private suspend fun startFaceVerificationInternal(
        context: Context,
        request: FaceVerificationRequest
    ) {
        val config = systemConfigManager.getFaceVerificationConfig()
        if (config == null) {
            emitError(
                message = "人脸验证配置不可用，请重新登录后重试",
                error = null,
                event = "shared_face_config_missing",
                description = "共享人脸验证配置缺失",
                extras = request.diagnosticExtras("config_missing"),
            )
            return
        }
        faceVerifier.startFaceVerification(
            context = context,
            config = config,
            request = request,
            callback = createFaceVerifyCallback(request)
        )
    }

    private fun createFaceVerifyCallback(request: FaceVerificationRequest) = buildFaceVerifyCallback(
        onInitSuccess = { _uiState.value = FaceVerifyUiState.Verifying },
        onInitFailed = { error ->
            emitError(
                message = "人脸验证初始化失败：${error.readableDescription()}",
                error = error,
                event = "shared_face_init_failed",
                description = "共享人脸验证初始化失败",
                extras = request.diagnosticExtras("init_failed"),
            )
        },
        onVerifySuccess = { result ->
            _uiState.value = FaceVerifyUiState.Success(result)
            _events.tryEmit(FaceVerifyEvent.Success(result))
        },
        onVerifyFailed = { error ->
            emitError(
                message = "人脸验证失败：${error.readableDescription()}",
                error = error,
                event = "shared_face_verify_failed",
                description = "共享人脸验证失败",
                extras = request.diagnosticExtras("verify_failed"),
            )
        },
        onVerifyCancel = {
            DiagnosticEventTracker.trackEvent(
                category = DIAGNOSTIC_CATEGORY,
                event = "shared_face_cancelled",
                description = "共享人脸验证取消",
                extras = request.diagnosticExtras("cancelled"),
            )
            _uiState.value = FaceVerifyUiState.Cancelled
            _events.tryEmit(FaceVerifyEvent.Cancelled)
        }
    )

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
        _events.tryEmit(FaceVerifyEvent.Error(message))
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
