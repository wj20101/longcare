package com.ytone.longcare.features.identification.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.features.identification.domain.UploadElderPhotoUseCase
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.models.protos.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
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

    private val _events = MutableSharedFlow<IdentificationEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<IdentificationEvent> = _events.asSharedFlow()

    private fun emitEvent(event: IdentificationEvent) {
        _events.tryEmit(event)
    }

    private fun setFaceVerificationError(message: String, error: FaceVerifyError? = null) {
        _faceVerificationState.value = FaceVerificationState.Error(error = error, message = message)
        emitEvent(IdentificationEvent.ShowToast(message))
    }

    private fun setFaceSetupError(message: String) {
        _faceSetupState.value = FaceSetupState.Error(message)
        emitEvent(IdentificationEvent.ShowToast(message))
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
        launchServicePersonVerification(
            scope = viewModelScope,
            context = context,
            resolveCurrentUser = ::getCurrentUser,
            verifyServicePersonUseCase = verifyServicePersonUseCase,
            createOrderNo = ::createServiceOrderNo,
            startVerificationWithBase64 = ::startSelfProvidedFaceVerificationWithBase64,
            startVerificationAndCache = ::startSelfProvidedFaceVerificationAndCache,
            onRequireFaceSetup = ::navigateToFaceCaptureForSetup,
            onError = { message ->
                logE(message, tag = "IdentificationVM")
                setFaceVerificationError(message)
            },
        )
    }

    private fun navigateToFaceCaptureForSetup() {
        emitEvent(IdentificationEvent.ShowToast("请先设置人脸信息"))
        emitEvent(IdentificationEvent.NavigateToFaceCapture)
    }
    
    /**
     * 验证老人
     */
    fun verifyElder(context: Context, request: OrderInfoRequestModel) {
        viewModelScope.launch {
            val payload = resolveElderVerificationPayload(
                orderKey = request.toOrderKey(),
                orderDetailRepository = unifiedOrderRepository,
            ) ?: return@launch

            startFaceVerification(
                context = context,
                name = payload.name,
                idNo = payload.idNo,
                orderNo = createElderOrderNo(orderId = request.orderId),
                userId = payload.userId,
                verificationType = VerificationType.ELDER
            )
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
        launchSelfProvidedFaceVerificationAndCache(
            scope = viewModelScope,
            context = context,
            name = name,
            idNo = idNo,
            orderNo = orderNo,
            userId = userId,
            sourcePhotoUrl = sourcePhotoUrl,
            faceDataSource = faceDataSource,
            resolveCurrentUser = ::getCurrentUser,
            beginVerification = ::beginVerification,
            startVerification = ::startSelfProvidedFaceVerification,
            onFailure = ::setFaceVerificationError,
        )
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
        launchSelfProvidedFaceVerificationWithBase64(
            scope = viewModelScope,
            context = context,
            name = name,
            idNo = idNo,
            orderNo = orderNo,
            userId = userId,
            sourcePhotoBase64 = sourcePhotoBase64,
            beginVerification = ::beginVerification,
            startVerification = ::startSelfProvidedFaceVerification,
            onFailure = { message, throwable ->
                logE("人脸验证失败", tag = "IdentificationVM", throwable = throwable)
                setFaceVerificationError(message)
            },
        )
    }

    private suspend fun startSelfProvidedFaceVerification(
        context: Context,
        name: String,
        idNo: String,
        orderNo: String,
        userId: String,
        sourcePhotoBase64: String,
    ) {
        val request = createFaceVerificationRequest(
            name = name,
            idNo = idNo,
            orderNo = orderNo,
            userId = userId,
            sourcePhotoBase64 = sourcePhotoBase64
        )
        startFaceVerificationWithDefaultCallback(context, request)
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
            beginVerification(verificationType)

            val request = createFaceVerificationRequest(
                name = name,
                idNo = idNo,
                orderNo = orderNo,
                userId = userId
            )

            startFaceVerificationWithDefaultCallback(context, request)
        }
    }

    private fun beginVerification(verificationType: VerificationType) {
        _currentVerificationType.value = verificationType
        _faceVerificationState.value = FaceVerificationState.Initializing
    }

    private suspend fun startFaceVerificationWithDefaultCallback(
        context: Context,
        request: FaceVerificationRequest,
    ) {
        startFaceVerificationWithResolvedConfigOrNotify(
            context = context,
            request = request,
            callback = createIdentificationFlowVerifyCallback(
                currentVerificationType = { _currentVerificationType.value },
                setVerificationState = { state -> _faceVerificationState.value = state },
                onSetFaceVerificationError = { message, error ->
                    setFaceVerificationError(message, error)
                },
                onServicePersonVerified = ::setServicePersonVerified,
                onElderVerified = ::setElderVerified,
                showToast = { message -> toastHelper.showShort(message) }
            ),
            systemConfigManager = systemConfigManager,
            faceVerifier = faceVerifier,
            onConfigMissing = { setFaceVerificationError("人脸配置不可用") }
        )
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
        launchElderPhotoUpload(
            scope = viewModelScope,
            uploadElderPhotoUseCase = uploadElderPhotoUseCase,
            photoUri = photoUri,
            orderId = request.orderId,
            onProcessing = { _photoUploadState.value = PhotoUploadState.Processing },
            onUploading = { _photoUploadState.value = PhotoUploadState.Uploading },
            onUploadSuccess = {
                _photoUploadState.value = PhotoUploadState.Success
                toastHelper.showShort("老人照片上传成功")
                setElderVerified()
                onSuccess()
            },
            onUploadError = { message ->
                _photoUploadState.value = PhotoUploadState.Error(message)
                toastHelper.showShort(message)
            },
            onUnexpectedError = { message ->
                _photoUploadState.value = PhotoUploadState.Error(message ?: "未知错误")
                toastHelper.showShort("处理失败: $message")
            }
        )
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
        launchFaceCaptureResultHandling(
            scope = viewModelScope,
            context = context,
            imagePath = imagePath,
            faceDataSource = faceDataSource,
            resolveCurrentUser = ::getCurrentUser,
            setupFaceUseCase = setupFaceUseCase,
            systemConfigManager = systemConfigManager,
            faceVerifier = faceVerifier,
            setFaceSetupState = { state -> _faceSetupState.value = state },
            setFaceVerificationState = { state -> _faceVerificationState.value = state },
            setFaceSetupError = ::setFaceSetupError,
            showToast = { message -> toastHelper.showShort(message) },
            onServicePersonVerified = ::setServicePersonVerified,
        )
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
        // no-op: navigation now uses SharedFlow one-off events.
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
