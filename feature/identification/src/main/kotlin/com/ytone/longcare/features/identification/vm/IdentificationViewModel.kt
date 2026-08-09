package com.ytone.longcare.features.identification.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.features.identification.domain.UploadElderPhotoUseCase
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ytone.longcare.common.utils.logE

@HiltViewModel
class IdentificationViewModel @Inject constructor(
    private val faceVerificationConfigProvider: FaceVerificationConfigProvider,
    private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val faceDataSource: IdentificationFaceDataSource,
    private val verifyServicePersonUseCase: VerifyServicePersonUseCase,
    private val uploadElderPhotoUseCase: UploadElderPhotoUseCase,
    private val setupFaceUseCase: SetupFaceUseCase,
) : ViewModel() {
    private val _identificationState = MutableStateFlow(IdentificationState.INITIAL)
    val identificationState: StateFlow<IdentificationState> = _identificationState.asStateFlow()
    
    private val _faceVerificationState = MutableStateFlow<FaceVerificationState>(FaceVerificationState.Idle)
    val faceVerificationState: StateFlow<FaceVerificationState> = _faceVerificationState.asStateFlow()
    
    private val _currentVerificationType = MutableStateFlow<VerificationType?>(null)
    val currentVerificationType: StateFlow<VerificationType?> = _currentVerificationType.asStateFlow()

    private val _photoUploadState = MutableStateFlow<PhotoUploadState>(PhotoUploadState.Initial)
    val photoUploadState: StateFlow<PhotoUploadState> = _photoUploadState.asStateFlow()
    
    private val _faceSetupState = MutableStateFlow<FaceSetupState>(FaceSetupState.Initial)
    val faceSetupState: StateFlow<FaceSetupState> = _faceSetupState.asStateFlow()

    private val uiActionQueue = IdentificationUiActionQueue()
    val pendingUiActions: StateFlow<List<IdentificationUiAction>> = uiActionQueue.actions

    private val faceSdkCoordinator = IdentificationFaceSdkCoordinator(
        configProvider = faceVerificationConfigProvider,
        onStandardConfigMissing = { setFaceVerificationError("人脸配置不可用") },
        onFaceSetupConfigMissing = { setFaceSetupError("人脸配置不可用") },
    )
    val faceSdkLaunchRequest = faceSdkCoordinator.launchRequest

    private fun setFaceVerificationError(message: String, error: FaceVerifyError? = null) {
        FaceVerificationEventTracker.trackError(
            eventType = faceVerificationErrorEventType(message),
            extras = FaceVerificationEventTracker.faceErrorExtras(
                error = error,
                extras = mapOf(
                    "verificationType" to _currentVerificationType.value,
                    "message" to message,
                ),
            ),
        )
        _faceVerificationState.value = FaceVerificationState.Error(error = error, message = message)
        showLongMessage(message)
    }

    private fun setFaceSetupError(message: String) {
        FaceVerificationEventTracker.trackError(
            eventType = EventType.FACE_SETUP_ERROR,
            extras = mapOf("message" to message),
        )
        _faceSetupState.value = FaceSetupState.Error(message)
        showLongMessage(message)
    }

    private fun faceVerificationErrorEventType(message: String): EventType {
        return when {
            message.contains("初始化") -> EventType.FACE_INIT_ERROR
            message.contains("获取人脸照片") || message.contains("人脸来源") -> EventType.SERVICE_FACE_SOURCE_ERROR
            else -> EventType.FACE_VERIFY_ERROR
        }
    }
    
    /** 优先使用本地人脸，其次下载服务端人脸，均不可用时进入设置流程。 */
    fun verifyServicePerson() {
        launchServicePersonVerification(
            scope = viewModelScope,
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
        uiActionQueue.enqueue(IdentificationUiEffect.NavigateToFaceCapture("请先设置人脸信息"))
    }
    
    /**
     * 验证老人
     */
    fun verifyElder(orderKey: OrderKey) {
        launchElderVerification(
            scope = viewModelScope,
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
        name: String,
        idNo: String,
        orderNo: String,
        userId: String,
        verificationType: VerificationType
    ) {
        launchStandardFaceVerification(
            scope = viewModelScope,
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
        request: FaceVerificationRequest,
    ) {
        faceSdkCoordinator.prepareStandard(request)
    }

    fun consumeFaceSdkLaunchRequest(id: Long) = faceSdkCoordinator.consume(id)

    fun onFaceSdkEvent(launchId: Long, event: FaceSdkEvent) {
        faceSdkCoordinator.dispatch(
            id = launchId,
            event = event,
            onStandard = { sdkEvent ->
                handleIdentificationFlowFaceSdkEvent(
                    event = sdkEvent,
                    currentVerificationType = { _currentVerificationType.value },
                    setVerificationState = { state -> _faceVerificationState.value = state },
                    onSetFaceVerificationError = ::setFaceVerificationError,
                    onServicePersonVerified = ::setServicePersonVerified,
                    onElderVerified = ::setElderVerified,
                    showToast = ::showShortMessage,
                )
            },
            onFaceSetup = { sdkEvent, ready ->
                handleStandardFaceSetupSdkEvent(
                    event = sdkEvent,
                    ready = ready,
                    scope = viewModelScope,
                    setupFaceUseCase = setupFaceUseCase,
                    resolveCurrentUserId = { getCurrentUser()?.userId },
                    setFaceSetupState = { state -> _faceSetupState.value = state },
                    setFaceSetupError = ::setFaceSetupError,
                    showToast = ::showShortMessage,
                    onServicePersonVerified = ::setServicePersonVerified,
                )
            },
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
            showToast = ::showShortMessage,
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

    fun resetPhotoUploadState() { _photoUploadState.value = PhotoUploadState.Initial }
    
    /**
     * 处理人脸捕获结果 - 用于首次设置人脸信息
     * @param imagePath 捕获的人脸图片路径
     */
    fun handleFaceCaptureResult(imagePath: String) {
        launchFaceCaptureResultHandling(
            scope = viewModelScope,
            imagePath = imagePath,
            faceDataSource = faceDataSource,
            resolveCurrentUser = ::getCurrentUser,
            setFaceSetupState = { state -> _faceSetupState.value = state },
            setFaceVerificationState = { state -> _faceVerificationState.value = state },
            setFaceSetupError = ::setFaceSetupError,
            showToast = ::showShortMessage,
            prepareSdkLaunch = faceSdkCoordinator::prepareFaceSetup,
        )
    }

    fun consumeUiAction(actionId: Long) {
        uiActionQueue.consume(actionId)
    }

    private fun showShortMessage(message: String) {
        uiActionQueue.enqueue(IdentificationUiEffect.ShowMessage(message))
    }

    private fun showLongMessage(message: String) {
        uiActionQueue.enqueue(IdentificationUiEffect.ShowMessage(message = message, long = true))
    }

    fun resetFaceSetupState() { _faceSetupState.value = FaceSetupState.Initial }
}
