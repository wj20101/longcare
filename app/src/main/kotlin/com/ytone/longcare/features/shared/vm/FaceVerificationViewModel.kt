package com.ytone.longcare.features.shared.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            emitError("人脸配置不可用", null)
            return
        }
        faceVerifier.startFaceVerification(
            context = context,
            config = config,
            request = request,
            callback = createFaceVerifyCallback()
        )
    }

    private fun createFaceVerifyCallback() = buildFaceVerifyCallback(
        onInitSuccess = { _uiState.value = FaceVerifyUiState.Verifying },
        onInitFailed = { error -> emitError("人脸识别初始化失败: ${error?.description ?: "未知错误"}", error) },
        onVerifySuccess = { result ->
            _uiState.value = FaceVerifyUiState.Success(result)
            _events.tryEmit(FaceVerifyEvent.Success(result))
        },
        onVerifyFailed = { error -> emitError("人脸验证失败: ${error?.description ?: "未知错误"}", error) },
        onVerifyCancel = {
            _uiState.value = FaceVerifyUiState.Cancelled
            _events.tryEmit(FaceVerifyEvent.Cancelled)
        }
    )

    private fun emitError(message: String, error: FaceVerifyError?) {
        _uiState.value = FaceVerifyUiState.Error(error = error, message = message)
        _events.tryEmit(FaceVerifyEvent.Error(message))
    }
}
