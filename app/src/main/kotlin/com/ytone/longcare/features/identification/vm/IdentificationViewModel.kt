package com.ytone.longcare.features.identification.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.request.SetFaceParamModel
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.SharedOrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.models.protos.User
import android.util.Base64
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ytone.longcare.data.storage.DataStoreKeys
import com.ytone.longcare.data.storage.UserSpecificDataStoreManager
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
    private val identificationRepository: IdentificationRepository,
    private val userSpecificDataStoreManager: UserSpecificDataStoreManager,
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
                // 优先本地缓存：读取用户人脸Base64
                val cachedBase64 = readUserFaceBase64(currentUser.userId)
                if (!cachedBase64.isNullOrBlank()) {
                    // 本地存在Base64，直接自带源比对
                    startSelfProvidedFaceVerificationWithBase64(
                        context = context,
                        name = currentUser.userName,
                        idNo = currentUser.identityCardNumber,
                        orderNo = "service_${System.currentTimeMillis()}",
                        userId = currentUser.userId.toString(),
                        sourcePhotoBase64 = cachedBase64
                    )
                } else {
                    // 本地无缓存，调用接口获取人脸地址
                    when (val faceResult = identificationRepository.getFace()) {
                        is ApiResult.Success -> {
                            val url = faceResult.data.faceImgUrl
                            if (url.isBlank()) {
                                // 无人脸数据，跳到人脸捕获
                                _navigateToFaceCapture.value = true
                            } else {
                                // 使用URL进行自带源比对（下载再比对）
                                startSelfProvidedFaceVerification(
                                    context = context,
                                    name = currentUser.userName,
                                    idNo = currentUser.identityCardNumber,
                                    orderNo = "service_${System.currentTimeMillis()}",
                                    userId = currentUser.userId.toString(),
                                    sourcePhotoUrl = url
                                )
                            }
                        }
                        is ApiResult.Failure, is ApiResult.Exception -> {
                            // 接口异常或失败，回退到人脸捕获
                            _navigateToFaceCapture.value = true
                        }
                    }
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
                    // 验证成功后，缓存当前使用的Base64
                    callback = createFaceVerifyCallbackWithCache(sourcePhotoBase64)
                )
            } catch (e: Exception) {
                _faceVerificationState.value = FaceVerificationState.Error(
                    error = null,
                    message = "获取人脸照片失败: ${e.message}"
                )
                // 链接访问失败或下载异常，回退到本地拍照逻辑
                _navigateToFaceCapture.value = true
            }
        }
    }

    /**
     * 开始自带源比对人脸验证（直接使用Base64）
     */
    private fun startSelfProvidedFaceVerificationWithBase64(
        context: Context,
        name: String,
        idNo: String,
        orderNo: String,
        userId: String,
        sourcePhotoBase64: String
    ) {
        viewModelScope.launch {
            _currentVerificationType.value = VerificationType.SERVICE_PERSON
            _faceVerificationState.value = FaceVerificationState.Initializing

            try {
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
                    // 验证成功后，缓存当前使用的Base64
                    callback = createFaceVerifyCallbackWithCache(sourcePhotoBase64)
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
     * 创建人脸验证回调（服务人员验证场景），在验证成功后更新本地人脸Base64缓存
     */
    private fun createFaceVerifyCallbackWithCache(base64ForCache: String?) = object : FaceVerificationManager.FaceVerifyCallback {
        override fun onInitSuccess() {
            toastHelper.showShort("人脸验证初始化成功")
            _faceVerificationState.value = FaceVerificationState.Verifying
        }

        override fun onInitFailed(error: WbFaceError?) {
            val errorMsg = "人脸识别初始化失败: ${error?.desc ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
            toastHelper.showShort(errorMsg)
            _faceVerificationState.value = FaceVerificationState.Error(
                error = error,
                message = errorMsg
            )
        }

        override fun onVerifySuccess(result: WbFaceVerifyResult) {
            toastHelper.showShort("人脸验证成功")
            _faceVerificationState.value = FaceVerificationState.Success(result)

            // 根据当前验证类型设置相应的身份验证状态
            when (_currentVerificationType.value) {
                VerificationType.SERVICE_PERSON -> {
                    setServicePersonVerified()
                    toastHelper.showShort("服务人员身份验证成功")

                    // 在服务人员自带源比对成功后，更新本地缓存
                    if (!base64ForCache.isNullOrBlank()) {
                        val sessionState = userSessionRepository.sessionState.value
                        if (sessionState is SessionState.LoggedIn) {
                            val userId = sessionState.user.userId
                            viewModelScope.launch {
                                writeUserFaceBase64(userId, base64ForCache)
                            }
                        }
                    }
                }
                VerificationType.ELDER -> {
                    setElderVerified()
                    toastHelper.showShort("老人身份验证成功")
                }
                null -> {
                    toastHelper.showShort("验证类型未知，请重新操作")
                }
            }
        }

        override fun onVerifyFailed(error: WbFaceError?) {
            val errorMsg = "人脸验证失败: ${error?.desc ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
            toastHelper.showShort(errorMsg)
            _faceVerificationState.value = FaceVerificationState.Error(
                error = error,
                message = errorMsg
            )
        }

        override fun onVerifyCancel() {
            toastHelper.showShort("人脸验证已取消")
            _faceVerificationState.value = FaceVerificationState.Cancelled
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
     * 创建人脸验证回调 - 用于正常的身份验证流程
     */
    private fun createFaceVerifyCallback() = object : FaceVerificationManager.FaceVerifyCallback {
        override fun onInitSuccess() {
            toastHelper.showShort("人脸验证初始化成功")
            _faceVerificationState.value = FaceVerificationState.Verifying
        }
        
        override fun onInitFailed(error: WbFaceError?) {
            val errorMsg = "人脸识别初始化失败: ${error?.desc ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
            toastHelper.showShort(errorMsg)
            _faceVerificationState.value = FaceVerificationState.Error(
                error = error,
                message = errorMsg
            )
        }
        
        override fun onVerifySuccess(result: WbFaceVerifyResult) {
            toastHelper.showShort("人脸验证成功")
            _faceVerificationState.value = FaceVerificationState.Success(result)
            
            // 根据当前验证类型设置相应的身份验证状态
            when (_currentVerificationType.value) {
                VerificationType.SERVICE_PERSON -> {
                    setServicePersonVerified()
                    toastHelper.showShort("服务人员身份验证成功")
                }
                VerificationType.ELDER -> {
                    setElderVerified()
                    toastHelper.showShort("老人身份验证成功")
                }
                null -> {
                    toastHelper.showShort("验证类型未知，请重新操作")
                }
            }
        }
        
        override fun onVerifyFailed(error: WbFaceError?) {
            val errorMsg = "人脸验证失败: ${error?.desc ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
            toastHelper.showShort(errorMsg)
            _faceVerificationState.value = FaceVerificationState.Error(
                error = error,
                message = errorMsg
            )
        }
        
        override fun onVerifyCancel() {
            toastHelper.showShort("人脸验证已取消")
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
     * 处理人脸捕获结果 - 用于首次设置人脸信息
     * @param context Activity Context，用于启动人脸验证
     * @param imagePath 捕获的人脸图片路径
     */
    fun handleFaceCaptureResult(context: Context, imagePath: String) {
        viewModelScope.launch {
            try {
                // 重置状态
                _faceSetupState.value = FaceSetupState.Initial
                _faceVerificationState.value = FaceVerificationState.Idle
                
                toastHelper.showShort("开始处理人脸图片...")
                
                // 检查图片文件是否存在
                val imageFile = File(imagePath)
                if (!imageFile.exists()) {
                    val errorMsg = "图片文件不存在: $imagePath"
                    toastHelper.showShort(errorMsg)
                    _faceSetupState.value = FaceSetupState.Error(errorMsg)
                    return@launch
                }
                
                // 转换图片为 Base64
                val base64Image = withContext(Dispatchers.IO) {
                    val bytes = imageFile.readBytes()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                
                // 获取当前用户信息
                val currentUser = getCurrentUser()
                if (currentUser == null) {
                    val errorMsg = "无法获取用户信息"
                    toastHelper.showShort(errorMsg)
                    _faceSetupState.value = FaceSetupState.Error(errorMsg)
                    return@launch
                }
                
                // 检查用户信息完整性
                if (currentUser.userName.isBlank() || currentUser.identityCardNumber.isBlank()) {
                    val errorMsg = "用户信息不完整，无法进行人脸验证"
                    toastHelper.showShort(errorMsg)
                    _faceSetupState.value = FaceSetupState.Error(errorMsg)
                    return@launch
                }
                
                toastHelper.showShort("开始人脸验证和设置...")
                
                // 创建人脸验证请求
                val request = FaceVerificationManager.FaceVerifyRequest(
                    name = currentUser.userName,
                    idNo = currentUser.identityCardNumber,
                    orderNo = "face_setup_${System.currentTimeMillis()}",
                    userId = currentUser.userId.toString(),
                    sourcePhotoStr = base64Image
                )
                
                // 启动人脸验证（用于设置人脸信息）
                faceVerificationManager.startFaceVerification(
                    context = context,
                    config = tencentCloudConfig,
                    request = request,
                    callback = createFaceSetupVerifyCallback(imageFile, base64Image)
                )
                
            } catch (e: Exception) {
                val errorMsg = "处理人脸图片时发生错误: ${e.message}"
                toastHelper.showShort(errorMsg)
                _faceSetupState.value = FaceSetupState.Error(errorMsg)
            }
        }
    }
    
    /**
     * 创建人脸设置验证回调 - 专门用于首次设置人脸信息
     */
    private fun createFaceSetupVerifyCallback(imageFile: File, base64Image: String) = 
        object : FaceVerificationManager.FaceVerifyCallback {
            override fun onInitSuccess() {
                toastHelper.showShort("人脸验证初始化成功")
                _faceSetupState.value = FaceSetupState.Initial
            }
            
            override fun onInitFailed(error: WbFaceError?) {
                val errorMsg = "人脸验证初始化失败: ${error?.desc ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
                toastHelper.showShort(errorMsg)
                _faceSetupState.value = FaceSetupState.Error(errorMsg)
            }
            
            override fun onVerifySuccess(result: WbFaceVerifyResult) {
                toastHelper.showShort("人脸验证成功，开始上传设置...")
                // 验证成功后，上传并设置人脸信息
                uploadAndSetFaceInfo(imageFile, base64Image)
            }
            
            override fun onVerifyFailed(error: WbFaceError?) {
                val errorMsg = "人脸验证失败: ${error?.desc ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
                toastHelper.showShort(errorMsg)
                _faceSetupState.value = FaceSetupState.Error(errorMsg)
            }
            
            override fun onVerifyCancel() {
                toastHelper.showShort("人脸验证已取消")
                _faceSetupState.value = FaceSetupState.Error("用户取消了人脸验证")
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
                    val setFaceResult = identificationRepository.setFace(
                        SetFaceParamModel(
                            faceImg = base64Image,
                            faceImgUrl = uploadResult.key
                        )
                    )
                    
                    when (setFaceResult) {
                        is ApiResult.Success -> {
                            _faceSetupState.value = FaceSetupState.UpdatingLocal
                            // 更新本地用户数据，并写入人脸Base64到用户特定的DataStore
                            val currentUser = getCurrentUser()
                            if (currentUser != null) {
                                val userId = currentUser.userId

                                // 构造用户特定的人脸Base64存储Key
                                val faceKey = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)

                                // 读取已有缓存，没有则通过接口获取URL并转换
                                val userDataStore = userSpecificDataStoreManager.userDataStore.value
                                if (userDataStore != null) {
                                    val existing = userDataStore.data.first()[faceKey]
                                    val base64ToStore = when {
                                        base64Image.isNotBlank() -> base64Image
                                        !existing.isNullOrBlank() -> existing
                                        else -> {
                                            // 本地无缓存则通过接口获取人脸地址并转换为Base64
                                            val faceResult = identificationRepository.getFace()
                                            val url = when (faceResult) {
                                                is ApiResult.Success -> faceResult.data.faceImgUrl
                                                is ApiResult.Failure -> ""
                                                is ApiResult.Exception -> ""
                                            }
                                            if (url.isNotBlank()) {
                                                try {
                                                    downloadAndConvertToBase64(url)
                                                } catch (e: Exception) {
                                                    ""
                                                }
                                            } else {
                                                ""
                                            }
                                        }
                                    }

                                    // 写入DataStore缓存
                                    if (base64ToStore.isNotBlank()) {
                                        userDataStore.edit { prefs ->
                                            prefs[faceKey] = base64ToStore
                                        }
                                    }
                                }

                                // 同步更新用户的 faceImgUrl（供服务器源比对模式使用）
                                // 同步更新用户会话（无需变更字段）
                                userSessionRepository.updateUser(currentUser)

                                _faceSetupState.value = FaceSetupState.Success
                                toastHelper.showShort("人脸信息设置成功")

                                // 设置成功后，更新身份认证状态
                                setServicePersonVerified()
                            } else {
                                val errorMsg = "更新本地用户数据失败：用户信息为空"
                                _faceSetupState.value = FaceSetupState.Error(errorMsg)
                                toastHelper.showShort(errorMsg)
                            }
                        }
                        is ApiResult.Failure -> {
                            val errorMsg = "服务器更新失败: ${setFaceResult.message}"
                            _faceSetupState.value = FaceSetupState.Error(errorMsg)
                            toastHelper.showShort(errorMsg)
                        }
                        is ApiResult.Exception -> {
                            val errorMsg = "网络请求异常: ${setFaceResult.exception.message}"
                            _faceSetupState.value = FaceSetupState.Error(errorMsg)
                            toastHelper.showShort(errorMsg)
                        }
                    }
                } else {
                    val errorMsg = uploadResult.errorMessage ?: "图片上传失败"
                    _faceSetupState.value = FaceSetupState.Error(errorMsg)
                    toastHelper.showShort(errorMsg)
                }
                
            } catch (e: Exception) {
                val errorMsg = "上传失败: ${e.message}"
                _faceSetupState.value = FaceSetupState.Error(errorMsg)
                toastHelper.showShort(errorMsg)
            }
        }
    }

    /**
     * 读取当前用户的人脸Base64缓存
     */
    private suspend fun readUserFaceBase64(userId: Int): String? {
        val ds: DataStore<Preferences> = userSpecificDataStoreManager.userDataStore.value ?: return null
        val key = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)
        val prefs = ds.data.first()
        return prefs[key]
    }

    /**
     * 写入当前用户的人脸Base64缓存
     */
    private suspend fun writeUserFaceBase64(userId: Int, base64: String) {
        val ds: DataStore<Preferences> = userSpecificDataStoreManager.userDataStore.value ?: return
        val key = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)
        ds.edit { prefs ->
            prefs[key] = base64
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
