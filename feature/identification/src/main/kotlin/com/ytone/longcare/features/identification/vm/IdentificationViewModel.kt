package com.ytone.longcare.features.identification.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.features.identification.domain.UploadElderPhotoUseCase
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.model.User
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
    private val faceVerificationConfigProvider: FaceVerificationConfigProvider,
    private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val faceDataSource: IdentificationFaceDataSource,
    private val verifyServicePersonUseCase: VerifyServicePersonUseCase,
    private val uploadElderPhotoUseCase: UploadElderPhotoUseCase,
    private val setupFaceUseCase: SetupFaceUseCase,
    private val toastHelper: ToastHelper,
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

    // 拍照上传状态
    private val _photoUploadState = MutableStateFlow<PhotoUploadState>(PhotoUploadState.Initial)
    val photoUploadState: StateFlow<PhotoUploadState> = _photoUploadState.asStateFlow()
    
    // 人脸设置状态
    private val _faceSetupState = MutableStateFlow<FaceSetupState>(FaceSetupState.Initial)
    val faceSetupState: StateFlow<FaceSetupState> = _faceSetupState.asStateFlow()

    private val _events = MutableSharedFlow<IdentificationEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<IdentificationEvent> = _events.asSharedFlow()

    private fun emitEvent(event: IdentificationEvent) = _events.tryEmit(event)

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
            faceDataSource = faceDataSource,
            beginVerification = ::beginVerification,
            startVerificationWithRequest = ::startFaceVerificationWithDefaultCallback,
            onRequireFaceSetup = ::navigateToFaceCaptureForSetup,
            onVerificationFailure = ::handleServicePersonVerificationFailure,
        )
    }

    private fun handleServicePersonVerificationFailure(message: String, throwable: Throwable?) {
        logE(message, tag = "IdentificationVM", throwable = throwable)
        setFaceVerificationError(message)
    }

    private fun navigateToFaceCaptureForSetup() {
        emitEvent(IdentificationEvent.ShowToast("请先设置人脸信息")); emitEvent(IdentificationEvent.NavigateToFaceCapture)
    }
    
    /**
     * 验证老人
     */
    fun verifyElder(context: Context, orderKey: OrderKey) {
        launchElderVerification(
            scope = viewModelScope,
            context = context,
            orderId = orderKey.orderId,
            orderKey = orderKey,
            orderDetailRepository = unifiedOrderRepository,
            startVerification = ::startFaceVerification,
        )
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
        launchStandardFaceVerification(
            scope = viewModelScope,
            context = context,
            name = name,
            idNo = idNo,
            orderNo = orderNo,
            userId = userId,
            verificationType = verificationType,
            beginVerification = ::beginVerification,
            startVerificationWithRequest = ::startFaceVerificationWithDefaultCallback,
        )
    }

    private fun beginVerification(verificationType: VerificationType) {
        _currentVerificationType.value = verificationType
        _faceVerificationState.value = FaceVerificationState.Initializing
    }

    private suspend fun startFaceVerificationWithDefaultCallback(
        context: Context,
        request: FaceVerificationRequest,
    ) {
        startFaceVerificationWithIdentificationBindings(
            context = context,
            request = request,
            currentVerificationType = { _currentVerificationType.value },
            setVerificationState = { state -> _faceVerificationState.value = state },
            onSetFaceVerificationError = ::setFaceVerificationError,
            onServicePersonVerified = ::setServicePersonVerified,
            onElderVerified = ::setElderVerified,
            showToast = toastHelper::showShort,
            configProvider = faceVerificationConfigProvider,
            faceVerifier = faceVerifier,
        )
    }
    
    /**
     * 获取当前登录用户
     */
    private suspend fun getCurrentUser(): User? =
        (userSessionRepository.sessionState.value as? SessionState.LoggedIn)?.user

    fun resetFaceVerificationState() { _faceVerificationState.value = FaceVerificationState.Idle; _currentVerificationType.value = null }

    fun setServicePersonVerified() { _identificationState.value = IdentificationState.SERVICE_VERIFIED }

    fun setElderVerified() { _identificationState.value = IdentificationState.ELDER_VERIFIED }

    fun updateFaceVerificationStatus(orderKey: OrderKey, verified: Boolean) {
        viewModelScope.launch {
            unifiedOrderRepository.updateFaceVerification(orderKey, verified)
        }
    }
    
    /**
     * 处理拍照并上传老人照片
     * @param photoUri 拍照的图片URI
     * @param orderKey 订单标识
     * @param onSuccess 成功回调
     */
    fun processElderPhoto(photoUri: Uri, orderKey: OrderKey, onSuccess: () -> Unit = {}) {
        launchElderPhotoUploadWithBindings(
            scope = viewModelScope,
            uploadElderPhotoUseCase = uploadElderPhotoUseCase,
            photoUri = photoUri,
            orderId = orderKey.orderId,
            photoUploadState = _photoUploadState,
            showToast = toastHelper::showShort,
            onElderVerified = ::setElderVerified,
            onSuccess = onSuccess,
        )
    }

    /**
     * 生成用于相机屏幕的水印数据
     * @param address 拍摄地址
     * @param orderKey 订单标识
     * @return WatermarkData
     */
    suspend fun generateWatermarkData(address: String, orderKey: OrderKey): WatermarkData {
        return generateIdentificationWatermarkData(
            address = address,
            orderKey = orderKey,
            orderDetailRepository = unifiedOrderRepository,
            resolveCurrentUser = ::getCurrentUser,
        )
    }

    fun showToast(message: String) = toastHelper.showShort(message)

    fun resetPhotoUploadState() { _photoUploadState.value = PhotoUploadState.Initial }
    
    /**
     * 处理人脸捕获结果 - 用于首次设置人脸信息
     * @param context Activity Context，用于启动人脸验证
     * @param imagePath 捕获的人脸图片路径
     */
    fun handleFaceCaptureResult(context: Context, imagePath: String) {
        launchFaceCaptureResultHandlingWithBindings(
            scope = viewModelScope,
            context = context,
            imagePath = imagePath,
            faceDataSource = faceDataSource,
            resolveCurrentUser = ::getCurrentUser,
            setupFaceUseCase = setupFaceUseCase,
            configProvider = faceVerificationConfigProvider,
            faceVerifier = faceVerifier,
            faceSetupState = _faceSetupState,
            faceVerificationState = _faceVerificationState,
            setFaceSetupError = ::setFaceSetupError,
            showToast = toastHelper::showShort,
            onServicePersonVerified = ::setServicePersonVerified,
        )
    }

    fun mockVerifyServicePerson() { setServicePersonVerified(); toastHelper.showShort("Mock: 服务人员验证通过") }

    fun mockVerifyElder() { setElderVerified(); toastHelper.showShort("Mock: 老人验证通过") }

    fun resetFaceSetupState() { _faceSetupState.value = FaceSetupState.Initial }

    override fun onCleared() { super.onCleared(); faceVerifier.release() }
}
