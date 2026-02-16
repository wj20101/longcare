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
    private val faceVerifier: FaceVerifier, private val systemConfigManager: SystemConfigManager, private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: OrderDetailRepository, private val faceDataSource: IdentificationFaceDataSource,
    private val verifyServicePersonUseCase: VerifyServicePersonUseCase, private val uploadElderPhotoUseCase: UploadElderPhotoUseCase, private val setupFaceUseCase: SetupFaceUseCase, private val toastHelper: ToastHelper,
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
    private val _events = MutableSharedFlow<IdentificationEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<IdentificationEvent> = _events.asSharedFlow()
    private fun emitEvent(event: IdentificationEvent) = _events.tryEmit(event)
    private fun setFaceVerificationError(message: String, error: FaceVerifyError? = null) = run { _faceVerificationState.value = FaceVerificationState.Error(error = error, message = message); emitEvent(IdentificationEvent.ShowToast(message)) }
    private fun setFaceSetupError(message: String) { _faceSetupState.value = FaceSetupState.Error(message); emitEvent(IdentificationEvent.ShowToast(message)) }
    fun verifyServicePerson(context: Context) = launchServicePersonVerification(
        scope = viewModelScope, context = context, resolveCurrentUser = ::getCurrentUser,
        verifyServicePersonUseCase = verifyServicePersonUseCase, createOrderNo = ::createServiceOrderNo, faceDataSource = faceDataSource,
        beginVerification = ::beginVerification, startVerificationWithRequest = ::startFaceVerificationWithDefaultCallback,
        onRequireFaceSetup = ::navigateToFaceCaptureForSetup, onVerificationFailure = ::handleServicePersonVerificationFailure,
    )
    private fun handleServicePersonVerificationFailure(message: String, throwable: Throwable?) { logE(message, tag = "IdentificationVM", throwable = throwable); setFaceVerificationError(message) }
    private fun navigateToFaceCaptureForSetup() { emitEvent(IdentificationEvent.ShowToast("请先设置人脸信息")); emitEvent(IdentificationEvent.NavigateToFaceCapture) }
    fun verifyElder(context: Context, request: OrderInfoRequestModel) = launchElderVerification(
        scope = viewModelScope, context = context, orderId = request.orderId, orderKey = request.toOrderKey(),
        orderDetailRepository = unifiedOrderRepository, startVerification = ::startFaceVerification,
    )
    private fun startFaceVerification(context: Context, name: String, idNo: String, orderNo: String, userId: String, verificationType: VerificationType) = launchStandardFaceVerification(
        scope = viewModelScope, context = context, name = name, idNo = idNo, orderNo = orderNo, userId = userId,
        verificationType = verificationType, beginVerification = ::beginVerification, startVerificationWithRequest = ::startFaceVerificationWithDefaultCallback,
    )
    private fun beginVerification(verificationType: VerificationType) { _currentVerificationType.value = verificationType; _faceVerificationState.value = FaceVerificationState.Initializing }
    private suspend fun startFaceVerificationWithDefaultCallback(context: Context, request: FaceVerificationRequest) = startFaceVerificationWithIdentificationBindings(
        context = context, request = request, currentVerificationType = { _currentVerificationType.value }, setVerificationState = { state -> _faceVerificationState.value = state },
        onSetFaceVerificationError = ::setFaceVerificationError, onServicePersonVerified = ::setServicePersonVerified, onElderVerified = ::setElderVerified,
        showToast = toastHelper::showShort, systemConfigManager = systemConfigManager, faceVerifier = faceVerifier,
    )
    private suspend fun getCurrentUser(): User? = (userSessionRepository.sessionState.value as? SessionState.LoggedIn)?.user
    fun resetFaceVerificationState() { _faceVerificationState.value = FaceVerificationState.Idle; _currentVerificationType.value = null }
    fun setServicePersonVerified() { _identificationState.value = IdentificationState.SERVICE_VERIFIED }
    fun setElderVerified() { _identificationState.value = IdentificationState.ELDER_VERIFIED }
    fun updateFaceVerificationStatus(request: OrderInfoRequestModel, verified: Boolean) = viewModelScope.launch { unifiedOrderRepository.updateFaceVerification(request.toOrderKey(), verified) }
    fun processElderPhoto(photoUri: Uri, request: OrderInfoRequestModel, onSuccess: () -> Unit = {}) = launchElderPhotoUploadWithBindings(
        scope = viewModelScope, uploadElderPhotoUseCase = uploadElderPhotoUseCase, photoUri = photoUri, orderId = request.orderId,
        photoUploadState = _photoUploadState, showToast = toastHelper::showShort, onElderVerified = ::setElderVerified, onSuccess = onSuccess,
    )
    suspend fun generateWatermarkData(address: String, request: OrderInfoRequestModel): WatermarkData = generateIdentificationWatermarkData(address = address, orderKey = request.toOrderKey(), orderDetailRepository = unifiedOrderRepository, resolveCurrentUser = ::getCurrentUser)
    fun showToast(message: String) = toastHelper.showShort(message)
    fun resetPhotoUploadState() { _photoUploadState.value = PhotoUploadState.Initial }
    fun handleFaceCaptureResult(context: Context, imagePath: String) = launchFaceCaptureResultHandlingWithBindings(
        scope = viewModelScope, context = context, imagePath = imagePath, faceDataSource = faceDataSource, resolveCurrentUser = ::getCurrentUser,
        setupFaceUseCase = setupFaceUseCase, systemConfigManager = systemConfigManager, faceVerifier = faceVerifier,
        faceSetupState = _faceSetupState, faceVerificationState = _faceVerificationState, setFaceSetupError = ::setFaceSetupError,
        showToast = toastHelper::showShort, onServicePersonVerified = ::setServicePersonVerified,
    )
    fun mockVerifyServicePerson() { setServicePersonVerified(); toastHelper.showShort("Mock: 服务人员验证通过") }
    fun mockVerifyElder() { setElderVerified(); toastHelper.showShort("Mock: 老人验证通过") }
    fun resetFaceSetupState() { _faceSetupState.value = FaceSetupState.Initial }
    override fun onCleared() { super.onCleared(); faceVerifier.release() }
}
