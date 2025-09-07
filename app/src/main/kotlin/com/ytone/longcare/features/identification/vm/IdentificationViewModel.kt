package com.ytone.longcare.features.identification.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.order.SharedOrderRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.photoupload.utils.ImageProcessor
import com.ytone.longcare.features.location.provider.CompositeLocationProvider
import com.ytone.longcare.models.protos.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
    private val sharedOrderRepository: SharedOrderRepository,
    private val orderRepository: OrderRepository,
    private val cosRepository: CosRepository,
    private val toastHelper: ToastHelper,
    private val compositeLocationProvider: CompositeLocationProvider,
    private val imageProcessor: ImageProcessor,
    @param:ApplicationContext private val applicationContext: Context
) : ViewModel() {
    
    // 移除重复的常量定义，使用统一的 CosConstants
    
    // 身份认证状态
    private val _identificationState = MutableStateFlow(IdentificationState.INITIAL)
    val identificationState: StateFlow<IdentificationState> = _identificationState.asStateFlow()
    
    // 人脸验证状态
    private val _faceVerificationState = MutableStateFlow<FaceVerificationState>(FaceVerificationState.Idle)
    val faceVerificationState: StateFlow<FaceVerificationState> = _faceVerificationState.asStateFlow()
    
    // 当前验证类型
    private val _currentVerificationType = MutableStateFlow<VerificationType?>(null)
    val currentVerificationType: StateFlow<VerificationType?> = _currentVerificationType.asStateFlow()

    // 拍照上传状态
    private val _photoUploadState = MutableStateFlow<PhotoUploadState>(PhotoUploadState.Initial)
    val photoUploadState: StateFlow<PhotoUploadState> = _photoUploadState.asStateFlow()
    
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
     * 拍照上传状态
     */
    sealed class PhotoUploadState {
        object Initial : PhotoUploadState()
        object Processing : PhotoUploadState()
        object Uploading : PhotoUploadState()
        object Success : PhotoUploadState()
        data class Error(val message: String) : PhotoUploadState()
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
            val orderInfo = sharedOrderRepository.getCachedOrderInfo(OrderInfoRequestModel(orderId = orderId, planId = 0))
            if (orderInfo != null) {
                val userInfo = orderInfo.userInfo
                if (userInfo != null) {
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
     * 获取当前定位信息
     */
    private suspend fun getCurrentLocationInfo(): String {
        return try {
            val locationResult = compositeLocationProvider.getCurrentLocation()
            if (locationResult != null) {
                "定位:${locationResult.longitude},${locationResult.latitude}"
            } else {
                "定位:未获取"
            }
        } catch (e: Exception) {
            "定位:获取失败"
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
    
    /**
     * 处理拍照并上传老人照片
     * @param photoUri 拍照的图片URI
     * @param orderId 订单ID
     * @param onSuccess 成功回调
     */
    fun processElderPhoto(photoUri: Uri, orderId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                _photoUploadState.value = PhotoUploadState.Processing
                
                // 生成水印内容
                val watermarkLines = generateWatermarkLines(orderId)
                
                // 处理图片（添加水印）
                val processResult = imageProcessor.processImage(photoUri, watermarkLines)
                
                if (processResult.isFailure) {
                    _photoUploadState.value = PhotoUploadState.Error(
                        processResult.exceptionOrNull()?.message ?: "图片处理失败"
                    )
                    return@launch
                }
                
                val processedUri = processResult.getOrNull()
                if (processedUri == null) {
                    _photoUploadState.value = PhotoUploadState.Error("图片处理结果为空")
                    return@launch
                }
                
                _photoUploadState.value = PhotoUploadState.Uploading
                
                // 使用CosUtils上传图片到云端
                val uploadParams = CosUtils.createUploadParams(
                    context = applicationContext,
                    fileUri = processedUri,
                    folderType = CosConstants.DEFAULT_FOLDER_TYPE
                )
                
                val uploadResult = cosRepository.uploadFile(uploadParams)
                
                if (uploadResult.success && uploadResult.key != null) {
                    // 上传成功，调用后端接口
                    when (val result = orderRepository.upUserStartImg(orderId, listOf(uploadResult.key))) {
                        is ApiResult.Success -> {
                            _photoUploadState.value = PhotoUploadState.Success
                            toastHelper.showShort("老人照片上传成功")
                            setElderVerified()
                            onSuccess()
                        }
                        is ApiResult.Exception -> {
                            val errorMessage = result.exception.message ?: "网络错误，请检查网络连接"
                            _photoUploadState.value = PhotoUploadState.Error(errorMessage)
                            toastHelper.showShort(errorMessage)
                        }
                        is ApiResult.Failure -> {
                            _photoUploadState.value = PhotoUploadState.Error(result.message)
                            toastHelper.showShort(result.message)
                        }
                    }
                } else {
                    // 上传失败
                    val errorMessage = uploadResult.errorMessage ?: "图片上传失败"
                    _photoUploadState.value = PhotoUploadState.Error(errorMessage)
                    toastHelper.showShort(errorMessage)
                }
                
            } catch (e: Exception) {
                _photoUploadState.value = PhotoUploadState.Error(e.message ?: "未知错误")
                toastHelper.showShort("处理失败: ${e.message}")
            }
        }
    }
    
    /**
     * 生成水印内容
     */
    private suspend fun generateWatermarkLines(orderId: Long): List<String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        // 获取订单信息
        val orderInfo = sharedOrderRepository.getCachedOrderInfo(OrderInfoRequestModel(orderId = orderId, planId = 0))
        val address = orderInfo?.userInfo?.address ?: "未知地址"
        val elderName = orderInfo?.userInfo?.name ?: "未知老人"
        
        // 获取当前登录用户（护工）信息
        val currentUser = getCurrentUser()
        val caregiverName = currentUser?.userName ?: "未知护工"
        
        // 获取定位信息
        val locationInfo = getCurrentLocationInfo()
        
        return listOf(
            "老人照片",
            "参保人:$elderName",
            "护工:$caregiverName",
            "时间: $currentTime",
            "地址: $address",
            locationInfo
        )
    }
    
    /**
     * 重置拍照上传状态
     */
    fun resetPhotoUploadState() {
        _photoUploadState.value = PhotoUploadState.Initial
    }

    override fun onCleared() {
        super.onCleared()
        // 释放人脸识别SDK资源
        faceVerificationManager.release()
    }
}