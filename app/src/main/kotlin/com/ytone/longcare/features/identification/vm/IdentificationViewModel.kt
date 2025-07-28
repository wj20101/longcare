package com.ytone.longcare.features.identification.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.domain.order.SharedOrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.models.protos.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 身份认证状态枚举
 */
enum class IdentificationState {
    INITIAL,           // 初始状态
    SERVICE_VERIFIED,  // 服务人员已验证
    ELDER_VERIFIED     // 老人已验证
}

@HiltViewModel
class IdentificationViewModel @Inject constructor(
    private val faceVerificationManager: FaceVerificationManager,
    private val userSessionRepository: UserSessionRepository,
    private val sharedOrderRepository: SharedOrderRepository
) : ViewModel() {
    
    // 身份认证状态
    private val _identificationState = MutableStateFlow(IdentificationState.INITIAL)
    val identificationState: StateFlow<IdentificationState> = _identificationState.asStateFlow()
    
    // 人脸验证状态
    private val _faceVerificationState = MutableStateFlow<FaceVerificationState>(FaceVerificationState.Idle)
    val faceVerificationState: StateFlow<FaceVerificationState> = _faceVerificationState.asStateFlow()
    
    // 当前验证类型
    private val _currentVerificationType = MutableStateFlow<VerificationType?>(null)
    val currentVerificationType: StateFlow<VerificationType?> = _currentVerificationType.asStateFlow()
    
    // 腾讯云配置
    private val tencentCloudConfig = FaceVerificationManager.TencentCloudConfig(
        appId = BuildConfig.TX_ID,
        secret = BuildConfig.TX_Secret
    )
    
    /**
     * 验证类型枚举
     */
    enum class VerificationType {
        SERVICE_PERSON,
        ELDER
    }
    
    /**
     * 人脸验证状态
     */
    sealed class FaceVerificationState {
        object Idle : FaceVerificationState()
        object Initializing : FaceVerificationState()
        object Verifying : FaceVerificationState()
        data class Success(val result: WbFaceVerifyResult) : FaceVerificationState()
        data class Error(val error: WbFaceError?, val message: String) : FaceVerificationState()
        object Cancelled : FaceVerificationState()
    }
    
    /**
     * 验证服务人员
     */
    fun verifyServicePerson(context: Context) {
        viewModelScope.launch {
            val currentUser = getCurrentUser()
            if (currentUser != null) {
                startFaceVerification(
                    context = context,
                    name = currentUser.userName,
                    idNo = currentUser.identityCardNumber,
                    orderNo = "service_${System.currentTimeMillis()}",
                    userId = currentUser.userId.toString(),
                    verificationType = VerificationType.SERVICE_PERSON
                )
            }
        }
    }
    
    /**
     * 验证老人
     */
    fun verifyElder(context: Context, orderId: Long) {
        viewModelScope.launch {
            val orderInfo = sharedOrderRepository.getCachedOrderInfo(orderId)
            if (orderInfo != null) {
                val userInfo = orderInfo.userInfo
                startFaceVerification(
                    context = context,
                    name = userInfo.name,
                    idNo = userInfo.identityCardNumber,
                    orderNo = "elder_${orderId}_${System.currentTimeMillis()}",
                    userId = userInfo.userId.toString(),
                    verificationType = VerificationType.ELDER
                )
            }
        }
    }
    
    /**
     * 开始人脸验证
     */
    private fun startFaceVerification(
        context: Context,
        name: String,
        idNo: String,
        orderNo: String,
        userId: String,
        verificationType: VerificationType
    ) {
        viewModelScope.launch {
            _currentVerificationType.value = verificationType
            _faceVerificationState.value = FaceVerificationState.Initializing
            
            val request = FaceVerificationManager.FaceVerifyRequest(
                name = name,
                idNo = idNo,
                orderNo = orderNo,
                userId = userId
            )
            
            faceVerificationManager.startFaceVerification(
                context = context,
                config = tencentCloudConfig,
                request = request,
                callback = createFaceVerifyCallback()
            )
        }
    }
    
    /**
     * 创建人脸验证回调
     */
    private fun createFaceVerifyCallback() = object : FaceVerificationManager.FaceVerifyCallback {
        override fun onInitSuccess() {
            _faceVerificationState.value = FaceVerificationState.Verifying
        }
        
        override fun onInitFailed(error: WbFaceError?) {
            _faceVerificationState.value = FaceVerificationState.Error(
                error = error,
                message = "人脸识别初始化失败: ${error?.desc ?: "未知错误"}"
            )
        }
        
        override fun onVerifySuccess(result: WbFaceVerifyResult) {
            _faceVerificationState.value = FaceVerificationState.Success(result)
        }
        
        override fun onVerifyFailed(error: WbFaceError?) {
            _faceVerificationState.value = FaceVerificationState.Error(
                error = error,
                message = "人脸验证失败: ${error?.desc ?: "未知错误"}"
            )
        }
        
        override fun onVerifyCancel() {
            _faceVerificationState.value = FaceVerificationState.Cancelled
        }
    }
    
    /**
     * 获取当前登录用户
     */
    private suspend fun getCurrentUser(): User? {
        return when (val sessionState = userSessionRepository.sessionState.value) {
            is SessionState.LoggedIn -> sessionState.user
            else -> null
        }
    }
    
    /**
     * 重置人脸验证状态
     */
    fun resetFaceVerificationState() {
        _faceVerificationState.value = FaceVerificationState.Idle
        _currentVerificationType.value = null
    }
    
    /**
     * 更新身份认证状态为服务人员已验证
     */
    fun setServicePersonVerified() {
        _identificationState.value = IdentificationState.SERVICE_VERIFIED
    }
    
    /**
     * 更新身份认证状态为老人已验证
     */
    fun setElderVerified() {
        _identificationState.value = IdentificationState.ELDER_VERIFIED
    }
    
    /**
     * 重置身份认证状态
     */
    fun resetState() {
        _identificationState.value = IdentificationState.INITIAL
        _faceVerificationState.value = FaceVerificationState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        // 释放人脸识别SDK资源
        faceVerificationManager.release()
    }
}