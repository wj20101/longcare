package com.ytone.longcare.features.identification.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.request.SetFaceParamModel
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.SharedOrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.models.protos.User
import android.util.Base64
import androidx.core.net.toUri
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val sharedOrderRepository: SharedOrderRepository,
    private val orderRepository: OrderRepository,
    private val cosRepository: CosRepository,
    private val apiService: LongCareApiService,
    private val toastHelper: ToastHelper,
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
    
    // 人脸设置状态
    private val _faceSetupState = MutableStateFlow<FaceSetupState>(FaceSetupState.Initial)
    val faceSetupState: StateFlow<FaceSetupState> = _faceSetupState.asStateFlow()
    
    // 导航到人脸捕获页面的状态
    private val _navigateToFaceCapture = MutableStateFlow(false)
    val navigateToFaceCapture: StateFlow<Boolean> = _navigateToFaceCapture.asStateFlow()
    
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
     * 人脸设置状态
     */
    sealed class FaceSetupState {
        object Initial : FaceSetupState()
        object UploadingImage : FaceSetupState()
        object UpdatingServer : FaceSetupState()
        object UpdatingLocal : FaceSetupState()
        object Success : FaceSetupState()
        data class Error(val message: String) : FaceSetupState()
    }
    
    /**
     * 验证服务人员
     */
    fun verifyServicePerson(context: Context) {
        viewModelScope.launch {
            val currentUser = getCurrentUser()
            if (currentUser != null) {
                // 检查用户是否已有人脸图片
                if (currentUser.faceImgUrl.isBlank()) {
                    // 如果没有人脸图片，跳转到人脸捕获页面
                    _navigateToFaceCapture.value = true
                } else {
                    // 如果有人脸图片，使用自带源比对模式
                    startSelfProvidedFaceVerification(
                        context = context,
                        name = currentUser.userName,
                        idNo = currentUser.identityCardNumber,
                        orderNo = "service_${System.currentTimeMillis()}",
                        userId = currentUser.userId.toString(),
                        sourcePhotoUrl = currentUser.faceImgUrl
                    )
                }
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
     * 开始自带源比对人脸验证
     */
    private fun startSelfProvidedFaceVerification(
        context: Context,
        name: String,
        idNo: String,
        orderNo: String,
        userId: String,
        sourcePhotoUrl: String
    ) {
        viewModelScope.launch {
            _currentVerificationType.value = VerificationType.SERVICE_PERSON
            _faceVerificationState.value = FaceVerificationState.Initializing
            
            try {
                // 下载源照片并转换为 Base64
                val sourcePhotoBase64 = downloadAndConvertToBase64(sourcePhotoUrl)
                
                val request = FaceVerificationManager.FaceVerifyRequest(
                    name = name,
                    idNo = idNo,
                    orderNo = orderNo,
                    userId = userId,
                    sourcePhotoStr = sourcePhotoBase64
                )
                
                faceVerificationManager.startFaceVerification(
                    context = context,
                    config = tencentCloudConfig,
                    request = request,
                    callback = createFaceVerifyCallback()
                )
            } catch (e: Exception) {
                _faceVerificationState.value = FaceVerificationState.Error(
                    error = null,
                    message = "获取人脸照片失败: ${e.message}"
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

                _photoUploadState.value = PhotoUploadState.Uploading
                
                // 使用CosUtils上传图片到云端
                val uploadParams = CosUtils.createUploadParams(
                    context = applicationContext,
                    fileUri = photoUri,
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
     * 生成用于相机屏幕的水印数据
     * @param address 拍摄地址
     * @param orderId 订单ID
     * @return WatermarkData
     */
    suspend fun generateWatermarkData(address: String, orderId: Long): WatermarkData {
        // 获取订单信息
        val orderInfo = sharedOrderRepository.getCachedOrderInfo(OrderInfoRequestModel(orderId = orderId, planId = 0))
        val elderName = orderInfo?.userInfo?.name ?: "未知老人"

        // 获取当前登录用户（护工）信息
        val currentUser = getCurrentUser()
        val caregiverName = currentUser?.userName ?: "未知护工"

        return WatermarkData(
            title = "老人照片",
            insuredPerson = elderName,
            caregiver = caregiverName,
            address = address
        )
    }

    /**
     * 显示一个Toast消息
     */
    fun showToast(message: String) {
        toastHelper.showShort(message)
    }
    
    /**
     * 重置拍照上传状态
     */
    fun resetPhotoUploadState() {
        _photoUploadState.value = PhotoUploadState.Initial
    }
    
    /**
     * 处理人脸捕获结果
     * @param imagePath 捕获的人脸图片路径
     */
    fun handleFaceCaptureResult(imagePath: String) {
        viewModelScope.launch {
            try {
                _faceSetupState.value = FaceSetupState.UploadingImage
                
                // 读取图片文件并转换为 Base64
                val imageFile = File(imagePath)
                if (!imageFile.exists()) {
                    _faceSetupState.value = FaceSetupState.Error("图片文件不存在")
                    return@launch
                }
                
                val imageBytes = imageFile.readBytes()
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                
                // 获取当前用户信息
                val currentUser = getCurrentUser()
                if (currentUser == null) {
                    _faceSetupState.value = FaceSetupState.Error("获取用户信息失败")
                    return@launch
                }
                
                // 先进行人脸验证（使用捕获的图片作为源比对）
                _currentVerificationType.value = VerificationType.SERVICE_PERSON
                _faceVerificationState.value = FaceVerificationState.Initializing
                
                val request = FaceVerificationManager.FaceVerifyRequest(
                    name = currentUser.userName,
                    idNo = currentUser.identityCardNumber,
                    orderNo = "service_${System.currentTimeMillis()}",
                    userId = currentUser.userId.toString(),
                    sourcePhotoStr = base64Image
                )
                
                faceVerificationManager.startFaceVerification(
                    context = applicationContext,
                    config = tencentCloudConfig,
                    request = request,
                    callback = createFaceSetupVerifyCallback(imageFile, base64Image)
                )
                
            } catch (e: Exception) {
                _faceSetupState.value = FaceSetupState.Error("处理失败: ${e.message}")
                toastHelper.showShort("处理失败: ${e.message}")
            }
        }
    }
    
    /**
     * 创建人脸设置验证回调（验证通过后才进行上传和设置）
     */
    private fun createFaceSetupVerifyCallback(imageFile: File, base64Image: String) = 
        object : FaceVerificationManager.FaceVerifyCallback {
            override fun onInitSuccess() {
                _faceVerificationState.value = FaceVerificationState.Verifying
            }
            
            override fun onInitFailed(error: WbFaceError?) {
                _faceSetupState.value = FaceSetupState.Error("人脸识别初始化失败: ${error?.desc ?: "未知错误"}")
                _faceVerificationState.value = FaceVerificationState.Error(
                    error = error,
                    message = "人脸识别初始化失败: ${error?.desc ?: "未知错误"}"
                )
            }
            
            override fun onVerifySuccess(result: WbFaceVerifyResult) {
                // 验证成功，开始上传图片和设置人脸信息
                _faceVerificationState.value = FaceVerificationState.Success(result)
                uploadAndSetFaceInfo(imageFile, base64Image)
            }
            
            override fun onVerifyFailed(error: WbFaceError?) {
                _faceSetupState.value = FaceSetupState.Error("人脸验证失败: ${error?.desc ?: "未知错误"}")
                _faceVerificationState.value = FaceVerificationState.Error(
                    error = error,
                    message = "人脸验证失败: ${error?.desc ?: "未知错误"}"
                )
            }
            
            override fun onVerifyCancel() {
                _faceSetupState.value = FaceSetupState.Error("用户取消了人脸验证")
                _faceVerificationState.value = FaceVerificationState.Cancelled
            }
        }
    
    /**
     * 上传图片并设置人脸信息（仅在验证通过后调用）
     */
    private fun uploadAndSetFaceInfo(imageFile: File, base64Image: String) {
        viewModelScope.launch {
            try {
                _faceSetupState.value = FaceSetupState.UploadingImage
                
                // 上传图片到 COS
                val uploadParams = CosUtils.createUploadParams(
                    context = applicationContext,
                    fileUri = imageFile.toUri(),
                    folderType = CosConstants.DEFAULT_FACE_TYPE
                )
                
                val uploadResult = cosRepository.uploadFile(uploadParams)
                
                if (uploadResult.success && uploadResult.key != null) {
                    _faceSetupState.value = FaceSetupState.UpdatingServer
                    
                    // 调用 setFace API
                    val setFaceResult = apiService.setFace(
                        SetFaceParamModel(
                            faceImg = base64Image,
                            faceImgUrl = uploadResult.key
                        )
                    )
                    
                    if (setFaceResult.isSuccess()) {
                        _faceSetupState.value = FaceSetupState.UpdatingLocal
                        
                        // 更新本地用户数据
                        val currentUser = getCurrentUser()
                        if (currentUser != null) {
                            val updatedUser = currentUser.copy(faceImgUrl = uploadResult.key)
                            userSessionRepository.updateUser(updatedUser)
                            
                            _faceSetupState.value = FaceSetupState.Success
                            toastHelper.showShort("人脸信息设置成功")
                            
                            // 设置成功后，更新身份认证状态
                            setServicePersonVerified()
                        }
                    } else {
                        _faceSetupState.value = FaceSetupState.Error("服务器更新失败")
                    }
                } else {
                    _faceSetupState.value = FaceSetupState.Error(uploadResult.errorMessage ?: "图片上传失败")
                }
                
            } catch (e: Exception) {
                _faceSetupState.value = FaceSetupState.Error("上传失败: ${e.message}")
                toastHelper.showShort("上传失败: ${e.message}")
            }
        }
    }
    
    /**
     * 下载图片并转换为 Base64
     */
    private suspend fun downloadAndConvertToBase64(imageUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                val inputStream = connection.getInputStream()
                val bytes = inputStream.readBytes()
                inputStream.close()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) {
                throw Exception("下载图片失败: ${e.message}")
            }
        }
    }
    
    /**
     * 重置导航状态
     */
    fun resetNavigationState() {
        _navigateToFaceCapture.value = false
    }
    
    /**
     * 重置人脸设置状态
     */
    fun resetFaceSetupState() {
        _faceSetupState.value = FaceSetupState.Initial
    }

    override fun onCleared() {
        super.onCleared()
        // 释放人脸识别SDK资源
        faceVerificationManager.release()
    }
}
