package com.ytone.longcare.features.identification.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.identification.domain.SetupFaceResult
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.features.identification.domain.UploadElderPhotoResult
import com.ytone.longcare.features.identification.domain.UploadElderPhotoUseCase
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDecision
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.models.protos.User
import java.io.File
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE

@HiltViewModel
class IdentificationViewModel @Inject constructor(
    private val faceVerifier: FaceVerifier,
    private val systemConfigManager: SystemConfigManager,
    private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val faceDataSource: IdentificationFaceDataSource,
    private val verifyServicePersonUseCase: VerifyServicePersonUseCase,
    private val uploadElderPhotoUseCase: UploadElderPhotoUseCase,
    private val setupFaceUseCase: SetupFaceUseCase,
    private val toastHelper: ToastHelper,
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
    private fun setFaceVerificationError(message: String, error: FaceVerifyError? = null) {
        _faceVerificationState.value = FaceVerificationState.Error(error = error, message = message)
        toastHelper.showShort(message)
    }
    private fun setFaceSetupError(message: String) {
        _faceSetupState.value = FaceSetupState.Error(message)
        toastHelper.showShort(message)
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
            when (val decision = verifyServicePersonUseCase.execute(getCurrentUser())) {
                is VerifyServicePersonDecision.UseCachedFace -> {
                    startSelfProvidedFaceVerificationWithBase64(
                        context = context,
                        name = decision.user.userName,
                        idNo = decision.user.identityCardNumber,
                        orderNo = "service_${System.currentTimeMillis()}",
                        userId = decision.user.userId.toString(),
                        sourcePhotoBase64 = decision.sourcePhotoBase64,
                    )
                }

                is VerifyServicePersonDecision.DownloadAndCache -> {
                    startSelfProvidedFaceVerificationAndCache(
                        context = context,
                        name = decision.user.userName,
                        idNo = decision.user.identityCardNumber,
                        orderNo = "service_${System.currentTimeMillis()}",
                        userId = decision.user.userId.toString(),
                        sourcePhotoUrl = decision.sourcePhotoUrl,
                    )
                }

                VerifyServicePersonDecision.RequireFaceSetup -> {
                    navigateToFaceCaptureForSetup()
                }

                is VerifyServicePersonDecision.Error -> {
                    logE(decision.message, tag = "IdentificationVM")
                    setFaceVerificationError(decision.message)
                }
            }
        }
    }

    private fun navigateToFaceCaptureForSetup() {
        toastHelper.showShort("请先设置人脸信息")
        _navigateToFaceCapture.value = true
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
                val sourcePhotoBase64 = faceDataSource.downloadAndConvertToBase64(sourcePhotoUrl)
                logD("下载成功，Base64长度: ${sourcePhotoBase64.length}", tag = "IdentificationVM")
                
                // 立即保存到本地缓存（在验证之前）
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    faceDataSource.writeUserFaceBase64(currentUser.userId, sourcePhotoBase64)
                    logD("已保存到本地缓存", tag = "IdentificationVM")
                }
                
                val request = FaceVerificationRequest(
                    name = name,
                    idNo = idNo,
                    orderNo = orderNo,
                    userId = userId,
                    sourcePhotoStr = sourcePhotoBase64
                )

                startFaceVerificationWithResolvedConfig(
                    context = context,
                    request = request,
                    // 使用普通回调即可，因为已经保存到本地了
                    callback = createFaceVerifyCallback(),
                    onConfigMissing = { setFaceVerificationError("人脸配置不可用") }
                )
            } catch (e: CancellationException) {
                throw e
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
                val request = FaceVerificationRequest(
                    name = name,
                    idNo = idNo,
                    orderNo = orderNo,
                    userId = userId,
                    sourcePhotoStr = sourcePhotoBase64
                )

                startFaceVerificationWithResolvedConfig(
                    context = context,
                    request = request,
                    // 使用普通回调即可，因为已经是从本地缓存读取的
                    callback = createFaceVerifyCallback(),
                    onConfigMissing = { setFaceVerificationError("人脸配置不可用") }
                )
            } catch (e: CancellationException) {
                throw e
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
            
            val request = FaceVerificationRequest(
                name = name,
                idNo = idNo,
                orderNo = orderNo,
                userId = userId
            )

            startFaceVerificationWithResolvedConfig(
                context = context,
                request = request,
                callback = createFaceVerifyCallback(),
                onConfigMissing = { setFaceVerificationError("人脸配置不可用") }
            )
        }
    }

    private suspend fun startFaceVerificationWithResolvedConfig(
        context: Context,
        request: FaceVerificationRequest,
        callback: FaceVerifyCallback,
        onConfigMissing: () -> Unit
    ) {
        val config = systemConfigManager.getFaceVerificationConfig()
        if (config == null) {
            onConfigMissing()
            return
        }

        faceVerifier.startFaceVerification(
            context = context,
            config = config,
            request = request,
            callback = callback
        )
    }
    
    /**
     * 创建人脸验证回调 - 用于正常的身份验证流程
     */
    private fun createFaceVerifyCallback() = object : FaceVerifyCallback {
        override fun onInitSuccess() {
            toastHelper.showShort("人脸验证初始化成功")
            _faceVerificationState.value = FaceVerificationState.Verifying
        }
        
        override fun onInitFailed(error: FaceVerifyError?) {
            val errorMsg = "人脸识别初始化失败: ${error?.description ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
            setFaceVerificationError(errorMsg, error)
        }
        
        override fun onVerifySuccess(result: FaceVerifyResult) {
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
        
        override fun onVerifyFailed(error: FaceVerifyError?) {
            val errorMsg = "人脸验证失败: ${error?.description ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
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

    fun updateFaceVerificationStatus(
        request: OrderInfoRequestModel,
        verified: Boolean
    ) {
        viewModelScope.launch {
            unifiedOrderRepository.updateFaceVerification(request.toOrderKey(), verified)
        }
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

                when (val result = uploadElderPhotoUseCase.execute(photoUri, request.orderId)) {
                    UploadElderPhotoResult.Success -> {
                        _photoUploadState.value = PhotoUploadState.Success
                        toastHelper.showShort("老人照片上传成功")
                        setElderVerified()
                        onSuccess()
                    }

                    is UploadElderPhotoResult.Error -> {
                        _photoUploadState.value = PhotoUploadState.Error(result.message)
                        toastHelper.showShort(result.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
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
                val base64Image = faceDataSource.imageFileToBase64(imageFile)
                
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
                val request = FaceVerificationRequest(
                    name = currentUser.userName,
                    idNo = currentUser.identityCardNumber,
                    orderNo = "face_setup_${System.currentTimeMillis()}",
                    userId = currentUser.userId.toString(),
                    sourcePhotoStr = base64Image
                )

                // 启动人脸验证（用于设置人脸信息）
                startFaceVerificationWithResolvedConfig(
                    context = context,
                    request = request,
                    callback = createFaceSetupVerifyCallback(imageFile, base64Image),
                    onConfigMissing = { setFaceSetupError("人脸配置不可用") }
                )
            } catch (e: CancellationException) {
                throw e
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
        object : FaceVerifyCallback {
            override fun onInitSuccess() {
                toastHelper.showShort("人脸验证初始化成功")
                _faceSetupState.value = FaceSetupState.Initial
            }
            
            override fun onInitFailed(error: FaceVerifyError?) {
                val errorMsg = "人脸验证初始化失败: ${error?.description ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
                setFaceSetupError(errorMsg)
            }
            
            override fun onVerifySuccess(result: FaceVerifyResult) {
                toastHelper.showShort("人脸验证成功，开始上传设置...")
                // 验证成功后，上传并设置人脸信息
                uploadAndSetFaceInfo(imageFile, base64Image)
            }
            
            override fun onVerifyFailed(error: FaceVerifyError?) {
                val errorMsg = "人脸验证失败: ${error?.description ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
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

                when (
                    val result = setupFaceUseCase.execute(
                        imageFile = imageFile,
                        base64Image = base64Image,
                        currentUser = getCurrentUser(),
                    )
                ) {
                    SetupFaceResult.Success -> {
                        _faceSetupState.value = FaceSetupState.Success
                        toastHelper.showShort("人脸信息设置成功")
                        setServicePersonVerified()
                    }

                    is SetupFaceResult.Error -> {
                        setFaceSetupError(result.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = "上传失败: ${e.message}"
                setFaceSetupError(errorMsg)
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
        faceVerifier.release()
    }
}
