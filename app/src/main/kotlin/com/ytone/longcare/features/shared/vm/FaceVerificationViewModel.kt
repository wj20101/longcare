package com.ytone.longcare.features.shared.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.common.utils.FaceVerificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 人脸验证ViewModel
 * 负责管理人脸验证的UI状态和业务逻辑
 */
@HiltViewModel
class FaceVerificationViewModel @Inject constructor(
    private val faceVerificationManager: FaceVerificationManager
) : ViewModel() {
    
    /**
     * 人脸验证UI状态
     */
    sealed class FaceVerifyUiState {
        object Idle : FaceVerifyUiState()
        object Initializing : FaceVerifyUiState()
        object Verifying : FaceVerifyUiState()
        data class Success(val result: WbFaceVerifyResult) : FaceVerifyUiState()
        data class Error(val error: WbFaceError?, val message: String) : FaceVerifyUiState()
        object Cancelled : FaceVerifyUiState()
    }
    
    private val _uiState = MutableStateFlow<FaceVerifyUiState>(FaceVerifyUiState.Idle)
    val uiState: StateFlow<FaceVerifyUiState> = _uiState.asStateFlow()

    /**
     * 开始人脸验证（自动获取签名参数）
     * @param config 腾讯云配置
     * @param faceId 人脸ID
     * @param orderNo 订单号
     * @param userId 用户ID
     */
    fun startFaceVerificationWithAutoSign(
        context: Context,
        config: FaceVerificationManager.TencentCloudConfig,
        faceId: String,
        orderNo: String,
        userId: String
    ) {
        viewModelScope.launch {
            _uiState.value = FaceVerifyUiState.Initializing

            faceVerificationManager.startFaceVerificationWithAutoSign(
                context = context,
                config = config,
                faceId = faceId,
                orderNo = orderNo,
                userId = userId,
                callback = createFaceVerifyCallback()
            )
        }
    }
    
    /**
     * 创建人脸验证回调
     */
    private fun createFaceVerifyCallback() = object : FaceVerificationManager.FaceVerifyCallback {
        override fun onInitSuccess() {
            _uiState.value = FaceVerifyUiState.Verifying
        }
        
        override fun onInitFailed(error: WbFaceError?) {
            _uiState.value = FaceVerifyUiState.Error(
                error = error,
                message = "人脸识别初始化失败: ${error?.desc ?: "未知错误"}"
            )
        }
        
        override fun onVerifySuccess(result: WbFaceVerifyResult) {
            _uiState.value = FaceVerifyUiState.Success(result)
        }
        
        override fun onVerifyFailed(error: WbFaceError?) {
            _uiState.value = FaceVerifyUiState.Error(
                error = error,
                message = "人脸验证失败: ${error?.desc ?: "未知错误"}"
            )
        }
        
        override fun onVerifyCancel() {
            _uiState.value = FaceVerifyUiState.Cancelled
        }
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        _uiState.value = FaceVerifyUiState.Idle
    }
    
    /**
     * 清理错误状态
     */
    fun clearError() {
        if (_uiState.value is FaceVerifyUiState.Error) {
            _uiState.value = FaceVerifyUiState.Idle
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 释放人脸识别SDK资源
        faceVerificationManager.release()
    }
}