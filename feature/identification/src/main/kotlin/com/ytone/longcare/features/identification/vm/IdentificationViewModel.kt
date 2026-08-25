package com.ytone.longcare.features.identification.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.features.identification.domain.UploadElderPhotoUseCase
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.User
import com.ytone.longcare.model.WatermarkData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class IdentificationViewModel @Inject constructor(
    private val faceVerificationConfigProvider: FaceVerificationConfigProvider,
    private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val faceDataSource: IdentificationFaceDataSource,
    private val verifyServicePersonUseCase: VerifyServicePersonUseCase,
    private val uploadElderPhotoUseCase: UploadElderPhotoUseCase,
    private val setupFaceUseCase: SetupFaceUseCase,
    private val textResolver: ResourceTextResolver,
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
        onStandardConfigMissing = {
            setFaceVerificationError(
                message = textResolver.text(R.string.identification_face_config_unavailable),
                eventType = EventType.FACE_INIT_ERROR,
            )
        },
        onFaceSetupConfigMissing = {
            setFaceSetupError(textResolver.text(R.string.identification_face_config_unavailable))
        },
    )
    val faceSdkLaunchRequest = faceSdkCoordinator.launchRequest
    private var servicePersonEntryJob: Job? = null

    private fun setFaceVerificationError(
        message: String,
        error: FaceVerifyError? = null,
        eventType: EventType = EventType.FACE_VERIFY_ERROR,
    ) {
        FaceVerificationEventTracker.trackError(
            eventType = eventType,
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

    fun verifyServicePerson(orderKey: OrderKey) {
        if (servicePersonEntryJob?.isActive == true) return

        beginVerification(VerificationType.SERVICE_PERSON)
        servicePersonEntryJob = launchServicePersonVerification(
            scope = viewModelScope,
            resolveCurrentUserId = { getCurrentUser()?.userId },
            verifyServicePersonUseCase = verifyServicePersonUseCase,
            onRegisteredFaceAvailable = { enqueueDefaultFaceVerification(orderKey) },
            onRequireFaceSetup = ::navigateToFaceCaptureForSetup,
            onSessionInvalidated = ::clearFaceVerificationState,
            onVerificationFailure = ::handleServicePersonVerificationFailure,
            textResolver = textResolver,
        )
    }

    private fun handleServicePersonVerificationFailure(message: String, throwable: Throwable?) {
        logE(message, tag = "IdentificationVM", throwable = throwable)
        setFaceVerificationError(
            message = message,
            eventType = EventType.SERVICE_FACE_SOURCE_ERROR,
        )
    }

    private fun navigateToFaceCaptureForSetup() {
        clearFaceVerificationState()
        _faceSetupState.value = FaceSetupState.Initial
        uiActionQueue.enqueue(IdentificationUiEffect.NavigateToFaceCapture(R.string.face_capture_setup_required))
    }

    private fun enqueueDefaultFaceVerification(orderKey: OrderKey) = uiActionQueue.enqueue(
        IdentificationUiEffect.NavigateToDefaultFaceVerification(orderKey),
    )

    fun verifyElder(orderKey: OrderKey) {
        launchElderVerification(
            scope = viewModelScope,
            orderId = orderKey.orderId,
            orderKey = orderKey,
            orderDetailRepository = unifiedOrderRepository,
            startVerification = ::startFaceVerification,
        )
    }

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
                    onSetFaceVerificationError = { message, error, eventType ->
                        setFaceVerificationError(message, error, eventType)
                    },
                    onServicePersonVerified = ::setServicePersonVerified,
                    onElderVerified = ::setElderVerified,
                    showToast = ::showShortMessage,
                    textResolver = textResolver,
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
                    textResolver = textResolver,
                )
            },
        )
    }

    private suspend fun getCurrentUser(): User? =
        (userSessionRepository.sessionState.value as? SessionState.LoggedIn)?.user

    private fun clearFaceVerificationState() {
        _faceVerificationState.value = FaceVerificationState.Idle
        _currentVerificationType.value = null
    }

    fun resetFaceVerificationState() {
        servicePersonEntryJob?.cancel()
        servicePersonEntryJob = null
        clearFaceVerificationState()
    }

    fun setServicePersonVerified() { _identificationState.value = IdentificationState.SERVICE_VERIFIED }

    fun setElderVerified() { _identificationState.value = IdentificationState.ELDER_VERIFIED }

    fun updateFaceVerificationStatus(orderKey: OrderKey, verified: Boolean) = viewModelScope.launch {
        unifiedOrderRepository.updateFaceVerification(orderKey, verified)
    }

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
            textResolver = textResolver,
        )
    }

    suspend fun generateWatermarkData(address: String, orderKey: OrderKey): WatermarkData =
        generateIdentificationWatermarkData(
            address = address,
            orderKey = orderKey,
            orderDetailRepository = unifiedOrderRepository,
            resolveCurrentUser = ::getCurrentUser,
            unknownElderName = textResolver.text(R.string.identification_watermark_unknown_elder),
            unknownCaregiverName = textResolver.text(
                R.string.identification_watermark_unknown_caregiver,
            ),
            watermarkTitle = textResolver.text(R.string.identification_watermark_elder_photo),
        )

    fun resetPhotoUploadState() { _photoUploadState.value = PhotoUploadState.Initial }

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
            textResolver = textResolver,
        )
    }

    fun consumeUiAction(actionId: Long) = uiActionQueue.consume(actionId)

    private fun showShortMessage(message: String) = uiActionQueue.enqueue(IdentificationUiEffect.ShowMessage(message))

    private fun showLongMessage(message: String) = uiActionQueue.enqueue(
        IdentificationUiEffect.ShowMessage(message = message, long = true),
    )

    fun resetFaceSetupState() { _faceSetupState.value = FaceSetupState.Initial }
}
