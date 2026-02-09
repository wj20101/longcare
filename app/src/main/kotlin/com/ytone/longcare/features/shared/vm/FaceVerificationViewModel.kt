package com.ytone.longcare.features.shared.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.FaceVerifyCallback
import com.ytone.longcare.common.utils.FaceVerifier
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
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
    private val faceVerifier: FaceVerifier,
    private val systemConfigManager: SystemConfigManager
) : ViewModel() {
    
    /**
     * 人脸验证UI状态
     */
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

    /**
     * 开始人脸验证（自动获取签名参数）
     * @param config 腾讯云配置
     * @param name 姓名
     * @param idNo 证件号码
     * @param orderNo 订单号
     * @param userId 用户ID
     */
    fun startFaceVerificationWithAutoSign(
        context: Context,
        name: String,
        idNo: String,
        orderNo: String,
        userId: String
    ) {
        viewModelScope.launch {
            _uiState.value = FaceVerifyUiState.Initializing

            val request = FaceVerificationRequest(
                name = name,
                idNo = idNo,
                orderNo = orderNo,
                userId = userId
            )
            startFaceVerificationInternal(context, request)
        }
    }
    
    /**
     * 开始人脸验证（自带源比对，自动获取签名参数）
     * @param config 腾讯云配置
     * @param orderNo 订单号
     * @param userId 用户ID
     * @param sourcePhotoStr 比对源照片(Base64)
     */
    fun startFaceVerificationWithAutoSign(
        context: Context,
        orderNo: String,
        userId: String,
        sourcePhotoStr: String
    ) {
        viewModelScope.launch {
            _uiState.value = FaceVerifyUiState.Initializing

            val request = FaceVerificationRequest(
                name = null, // 自带源比对时不需要姓名
                idNo = null, // 自带源比对时不需要身份证号
                orderNo = orderNo,
                userId = userId,
                sourcePhotoStr = sourcePhotoStr
            )
            startFaceVerificationInternal(context, request)
        }
    }

    private suspend fun startFaceVerificationInternal(
        context: Context,
        request: FaceVerificationRequest
    ) {
        val config = resolveFaceConfig()
        if (config == null) {
            _uiState.value = FaceVerifyUiState.Error(error = null, message = "人脸配置不可用")
            return
        }

        faceVerifier.startFaceVerification(
            context = context,
            config = config,
            request = request,
            callback = createFaceVerifyCallback()
        )
    }

    private suspend fun resolveFaceConfig(): FaceVerificationConfig? {
        val third = systemConfigManager.getThirdKey() ?: return null
        if (third.txFaceAppId.isBlank() || third.txFaceAppSecret.isBlank() || third.txFaceAppLicence.isBlank()) {
            return null
        }
        return FaceVerificationConfig(
            appId = third.txFaceAppId,
            secret = third.txFaceAppSecret,
            licence = third.txFaceAppLicence
        )
    }
    
    /**
     * 创建人脸验证回调
     */
    private fun createFaceVerifyCallback() = object : FaceVerifyCallback {
        override fun onInitSuccess() {
            _uiState.value = FaceVerifyUiState.Verifying
        }
        
        override fun onInitFailed(error: FaceVerifyError?) {
            _uiState.value = FaceVerifyUiState.Error(
                error = error,
                message = "人脸识别初始化失败: ${error?.description ?: "未知错误"}"
            )
        }
        
        override fun onVerifySuccess(result: FaceVerifyResult) {
            _uiState.value = FaceVerifyUiState.Success(result)
        }
        
        override fun onVerifyFailed(error: FaceVerifyError?) {
            _uiState.value = FaceVerifyUiState.Error(
                error = error,
                message = "人脸验证失败: ${error?.description ?: "未知错误"}"
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
        faceVerifier.release()
    }
}
