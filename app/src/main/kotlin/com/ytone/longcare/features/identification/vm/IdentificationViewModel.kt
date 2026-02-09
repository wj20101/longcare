package com.ytone.longcare.features.identification.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.request.SetFaceParamModel
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.toOrderKey
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
import com.ytone.longcare.model.isSucceed
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
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE

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
    private val systemConfigManager: SystemConfigManager,
    private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: UnifiedOrderRepository,
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
    private fun setFaceVerificationError(message: String, error: WbFaceError? = null) {
        _faceVerificationState.value = FaceVerificationState.Error(error = error, message = message)
        toastHelper.showShort(message)
    }
    private fun setFaceSetupError(message: String) {
        _faceSetupState.value = FaceSetupState.Error(message)
        toastHelper.showShort(message)
    }
    
    private suspend fun getTencentCloudConfig(): FaceVerificationManager.TencentCloudConfig? {
        val third = systemConfigManager.getThirdKey() ?: return null
        if (third.txFaceAppId.isBlank() || third.txFaceAppSecret.isBlank() || third.txFaceAppLicence.isBlank()) return null
        return FaceVerificationManager.TencentCloudConfig(
            appId = third.txFaceAppId,
            secret = third.txFaceAppSecret,
            licence = third.txFaceAppLicence
        )
    }
    
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
     * 
     * 业务流程：
     * 1. 检查本地缓存 → 有则使用
     * 2. 调用接口获取 → 有则下载并保存到本地
     * 3. 本地和接口都没有 → 跳转到人脸捕获
     */
    fun verifyServicePerson(context: Context) {
        viewModelScope.launch {
            val currentUser = getCurrentUser()
            if (currentUser == null) {
                logE("无法获取用户信息", tag = "IdentificationVM")
                toastHelper.showShort("无法获取用户信息")
                return@launch
            }
            
            logD("开始验证服务人员 (userId=${currentUser.userId}, userName=${currentUser.userName})", tag = "IdentificationVM")
            
            // 步骤1：优先检查本地缓存
            val cachedBase64 = readUserFaceBase64(currentUser.userId)
            
            if (!cachedBase64.isNullOrBlank()) {
                // ✅ 本地存在Base64，直接使用缓存进行验证
                logD("步骤1: 使用本地缓存进行验证 (userId=${currentUser.userId}, 长度=${cachedBase64.length})", tag = "IdentificationVM")
                startSelfProvidedFaceVerificationWithBase64(
                    context = context,
                    name = currentUser.userName,
                    idNo = currentUser.identityCardNumber,
                    orderNo = "service_${System.currentTimeMillis()}",
                    userId = currentUser.userId.toString(),
                    sourcePhotoBase64 = cachedBase64
                )
            } else {
                // 步骤2：本地无缓存，调用接口获取
                logD("步骤2: 本地无缓存，从服务器获取人脸信息", tag = "IdentificationVM")
                when (val faceResult = identificationRepository.getFace()) {
                    is ApiResult.Success -> {
                        val url = faceResult.data.faceImgUrl
                        if (url.isBlank()) {
                            // 步骤3：接口也没有数据，跳到人脸捕获
                            logD("步骤3: 服务器无人脸数据，跳转到人脸捕获", tag = "IdentificationVM")
                            toastHelper.showShort("请先设置人脸信息")
                            _navigateToFaceCapture.value = true
                        } else {
                            // 接口有数据，下载并保存到本地，然后验证
                            logD("步骤2: 从服务器下载人脸图片并保存到本地", tag = "IdentificationVM")
                            startSelfProvidedFaceVerificationAndCache(
                                context = context,
                                name = currentUser.userName,
                                idNo = currentUser.identityCardNumber,
                                orderNo = "service_${System.currentTimeMillis()}",
                                userId = currentUser.userId.toString(),
                                sourcePhotoUrl = url
                            )
                        }
                    }
                    is ApiResult.Failure -> {
                        // 接口成功，data为null，默认走成功且没数据流程
                        if (faceResult.code.isSucceed()) {
                            // 步骤3：接口返回成功但无数据，跳转到人脸捕获
                            logD("步骤3: 接口返回成功但无人脸数据，跳转到人脸捕获", tag = "IdentificationVM")
                            toastHelper.showShort("请先设置人脸信息")
                            _navigateToFaceCapture.value = true
                } else {
                    // 接口失败，提示错误
                    logE("获取人脸信息失败: ${faceResult.message}", tag = "IdentificationVM")
                    setFaceVerificationError(faceResult.message)
                }
            }
            is ApiResult.Exception -> {
                // 网络异常，提示错误
                logE("网络异常: ${faceResult.exception.message}", tag = "IdentificationVM")
                setFaceVerificationError("网络异常: ${faceResult.exception.message}")
            }
        }
            }
        }
    }
    
    /**
     * 验证老人
     */
    fun verifyElder(context: Context, request: OrderInfoRequestModel) {
        viewModelScope.launch {
            val orderInfo = unifiedOrderRepository.getCachedOrderInfo(request.toOrderKey())
            if (orderInfo != null) {
                val userInfo = orderInfo.userInfo
                if (userInfo != null) {
                    startFaceVerification(
                        context = context,
                        name = userInfo.name,
                        idNo = userInfo.identityCardNumber,
                        orderNo = "elder_${request.orderId}_${System.currentTimeMillis()}",
                        userId = userInfo.userId.toString(),
                        verificationType = VerificationType.ELDER
                    )
                }
            }
        }
    }
    
    /**
     * 开始自带源比对人脸验证（从URL下载并缓存到本地）
     * 
     * 用于场景：用户卸载重装后，本地无缓存，从服务器下载
     */
    private fun startSelfProvidedFaceVerificationAndCache(
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
                logD("从服务器下载人脸图片: $sourcePhotoUrl", tag = "IdentificationVM")
                // 下载源照片并转换为 Base64
                val sourcePhotoBase64 = downloadAndConvertToBase64(sourcePhotoUrl)
                logD("下载成功，Base64长度: ${sourcePhotoBase64.length}", tag = "IdentificationVM")
                
                // 立即保存到本地缓存（在验证之前）
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    writeUserFaceBase64(currentUser.userId, sourcePhotoBase64)
                    logD("已保存到本地缓存", tag = "IdentificationVM")
                }
                
                val request = FaceVerificationManager.FaceVerifyRequest(
                    name = name,
                    idNo = idNo,
                    orderNo = orderNo,
                    userId = userId,
                    sourcePhotoStr = sourcePhotoBase64
                )
                
                val cfg = getTencentCloudConfig()
                if (cfg == null) {
                    setFaceVerificationError("人脸配置不可用")
                    return@launch
                }
                faceVerificationManager.startFaceVerification(
                    context = context,
                    config = cfg,
                    request = request,
                    // 使用普通回调即可，因为已经保存到本地了
                    callback = createFaceVerifyCallback()
                )
            } catch (e: Exception) {
                logE("下载人脸图片失败", tag = "IdentificationVM", throwable = e)
                val errorMsg = "获取人脸照片失败: ${e.message}"
                setFaceVerificationError(errorMsg)
                // 下载失败，提示用户重试
            }
        }
    }

    /**
     * 开始自带源比对人脸验证（直接使用Base64）
     * 
     * 用于场景：使用本地缓存进行验证
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

                val cfg = getTencentCloudConfig()
                if (cfg == null) {
                    setFaceVerificationError("人脸配置不可用")
                    return@launch
                }
                faceVerificationManager.startFaceVerification(
                    context = context,
                    config = cfg,
                    request = request,
                    // 使用普通回调即可，因为已经是从本地缓存读取的
                    callback = createFaceVerifyCallback()
                )
            } catch (e: Exception) {
                logE("人脸验证失败", tag = "IdentificationVM", throwable = e)
                setFaceVerificationError("人脸验证失败: ${e.message}")
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
            
            val cfg = getTencentCloudConfig()
            if (cfg == null) {
                setFaceVerificationError("人脸配置不可用")
                return@launch
            }
            faceVerificationManager.startFaceVerification(
                context = context,
                config = cfg,
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
            setFaceVerificationError(errorMsg, error)
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
            setFaceVerificationError(errorMsg, error)
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
     * @param request 订单请求模型
     * @param onSuccess 成功回调
     */
    fun processElderPhoto(photoUri: Uri, request: OrderInfoRequestModel, onSuccess: () -> Unit = {}) {
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
                    when (val result = orderRepository.upUserStartImg(request.orderId, listOf(uploadResult.key))) {
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
     * @param request 订单请求模型
     * @return WatermarkData
     */
    suspend fun generateWatermarkData(address: String, request: OrderInfoRequestModel): WatermarkData {
        // 获取订单信息
        val orderInfo = unifiedOrderRepository.getCachedOrderInfo(request.toOrderKey())
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
                    setFaceSetupError(errorMsg)
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
                    setFaceSetupError(errorMsg)
                    return@launch
                }
                
                // 检查用户信息完整性
                if (currentUser.userName.isBlank() || currentUser.identityCardNumber.isBlank()) {
                    val errorMsg = "用户信息不完整，无法进行人脸验证"
                    setFaceSetupError(errorMsg)
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
                val cfg = getTencentCloudConfig()
                if (cfg == null) {
                    val errorMsg = "人脸配置不可用"
                    setFaceSetupError(errorMsg)
                    return@launch
                }
                faceVerificationManager.startFaceVerification(
                    context = context,
                    config = cfg,
                    request = request,
                    callback = createFaceSetupVerifyCallback(imageFile, base64Image)
                )
                
            } catch (e: Exception) {
                val errorMsg = "处理人脸图片时发生错误: ${e.message}"
                setFaceSetupError(errorMsg)
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
                setFaceSetupError(errorMsg)
            }
            
            override fun onVerifySuccess(result: WbFaceVerifyResult) {
                toastHelper.showShort("人脸验证成功，开始上传设置...")
                // 验证成功后，上传并设置人脸信息
                uploadAndSetFaceInfo(imageFile, base64Image)
            }
            
            override fun onVerifyFailed(error: WbFaceError?) {
                val errorMsg = "人脸验证失败: ${error?.desc ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
                setFaceSetupError(errorMsg)
            }
            
            override fun onVerifyCancel() {
                setFaceSetupError("用户取消了人脸验证")
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
                                
                                logD("开始保存人脸信息到本地 (userId=$userId)", tag = "IdentificationVM")

                                // 使用统一的写入方法写入人脸Base64
                                writeUserFaceBase64(userId, base64Image)

                                // 同步更新用户会话
                                userSessionRepository.updateUser(currentUser)

                                _faceSetupState.value = FaceSetupState.Success
                                toastHelper.showShort("人脸信息设置成功")

                                // 设置成功后，更新身份认证状态
                                setServicePersonVerified()
                            } else {
                                val errorMsg = "更新本地用户数据失败：用户信息为空"
                                setFaceSetupError(errorMsg)
                            }
                        }
                        is ApiResult.Failure -> {
                            val errorMsg = "服务器更新失败: ${setFaceResult.message}"
                            setFaceSetupError(errorMsg)
                        }
                        is ApiResult.Exception -> {
                            val errorMsg = "网络请求异常: ${setFaceResult.exception.message}"
                            setFaceSetupError(errorMsg)
                        }
                    }
                } else {
                    val errorMsg = uploadResult.errorMessage ?: "图片上传失败"
                    setFaceSetupError(errorMsg)
                }
                
            } catch (e: Exception) {
                val errorMsg = "上传失败: ${e.message}"
                setFaceSetupError(errorMsg)
            }
        }
    }

    /**
     * 获取用户的DataStore实例
     * 使用UserSpecificDataStoreManager确保单例，避免创建多个DataStore实例
     */
    private fun getUserDataStore(userId: Int): DataStore<Preferences> {
        return userSpecificDataStoreManager.getDataStoreForUser(userId)
    }
    
    /**
     * 读取当前用户的人脸Base64缓存
     */
    private suspend fun readUserFaceBase64(userId: Int): String? {
        return try {
            val ds = getUserDataStore(userId)
            val key = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)
            val prefs = ds.data.first()
            val result = prefs[key]
            
            if (result != null) {
                logD("成功读取人脸缓存 (userId=$userId, 长度=${result.length})", tag = "IdentificationVM")
            } else {
                logD("人脸缓存为空 (userId=$userId)", tag = "IdentificationVM")
            }
            
            result
        } catch (e: Exception) {
            logE("读取人脸缓存异常 (userId=$userId)", tag = "IdentificationVM", throwable = e)
            null
        }
    }

    /**
     * 写入当前用户的人脸Base64缓存
     */
    private suspend fun writeUserFaceBase64(userId: Int, base64: String) {
        try {
            val ds = getUserDataStore(userId)
            val key = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)
            
            ds.edit { prefs ->
                prefs[key] = base64
            }
            logD("成功写入人脸缓存 (userId=$userId, 长度=${base64.length})", tag = "IdentificationVM")
        } catch (e: Exception) {
            logE("写入人脸缓存异常 (userId=$userId)", tag = "IdentificationVM", throwable = e)
        }
    }

    /**
     * 下载图片并转换为Base64
     */
    private suspend fun downloadAndConvertToBase64(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = URL(url).readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) {
                logE("下载图片失败: $url", tag = "IdentificationVM", throwable = e)
                throw e
            }
        }
    }

    /**
     * 模拟服务人员验证通过 (Mock模式)
     */
    fun mockVerifyServicePerson() {
        setServicePersonVerified()
        toastHelper.showShort("Mock: 服务人员验证通过")
    }

    /**
     * 模拟老人验证通过 (Mock模式)
     */
    fun mockVerifyElder() {
        setElderVerified()
        toastHelper.showShort("Mock: 老人验证通过")
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
