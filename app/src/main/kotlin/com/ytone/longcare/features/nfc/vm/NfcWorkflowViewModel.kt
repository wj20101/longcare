package com.ytone.longcare.features.nfc.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.ExternalRfidReaderManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.features.nfc.ui.R65cWorkflowHidCapturedKeyEvent
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.ServiceCompleteData
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.platform.nfc.NfcDeviceCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NFC签到页面的ViewModel
 */
@HiltViewModel
class NfcWorkflowViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val serviceOrderLifecycle: ServiceOrderLifecycle,
    private val appEventBus: AppEventBus,
    private val externalRfidReaderManager: ExternalRfidReaderManager,
    private val locationFacade: LocationFacade,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val serviceCountdownSystemGateway: ServiceCountdownSystemGateway,
    private val nfcDeviceCapabilities: NfcDeviceCapabilities,
    textResolver: ResourceTextResolver,
) : ViewModel() {

    private val userMessages = textResolver.nfcUserMessages()

    private val _uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    val uiState: StateFlow<NfcSignInUiState> = _uiState.asStateFlow()

    private val _pendingNfcData = MutableStateFlow<PendingNfcData?>(null)
    val pendingNfcData: StateFlow<PendingNfcData?> = _pendingNfcData.asStateFlow()

    private val orderDelegate = NfcOrderWorkflowDelegate(
        serviceOrderLifecycle = serviceOrderLifecycle,
        locationFacade = locationFacade,
        textResolver = textResolver,
        orderRepository = orderRepository,
        unifiedOrderRepository = unifiedOrderRepository,
        imageRepository = imageRepository,
        serviceCountdownSystemGateway = serviceCountdownSystemGateway,
        uiState = _uiState,
        userMessages = userMessages,
    )

    private val locationDelegate = NfcLocationDelegate(
        locationFacade = locationFacade,
        textResolver = textResolver,
    )

    private val _scanMode = MutableStateFlow(selectScanMode(nfcDeviceCapabilities.isNfcSupported()))
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _readerUiState = MutableStateFlow(
        if (_scanMode.value == ScanMode.SYSTEM_NFC) {
            ReaderUiState.NotRequired
        } else {
            initialExternalReaderUiState(externalRfidReaderManager.isReaderReady())
        },
    )
    val readerUiState: StateFlow<ReaderUiState> = _readerUiState.asStateFlow()

    private val scanDelegate = NfcScanWorkflowDelegate(
        locationFacade = locationFacade,
        acquireLocation = locationDelegate::acquireLocation,
        appEventBus = appEventBus,
        unifiedOrderRepository = unifiedOrderRepository,
        orderRepository = orderRepository,
        scope = viewModelScope,
        uiState = _uiState,
        pendingNfcData = _pendingNfcData,
        scanMode = _scanMode,
        readerUiState = _readerUiState,
        orderDelegate = orderDelegate,
        userMessages = userMessages,
    )
    private val r65cSessionCollector = R65cWorkflowHidSessionCollector()
    private var r65cCompletionJob: kotlinx.coroutines.Job? = null
    private var orderActionJob: kotlinx.coroutines.Job? = null
    private var activeWorkflow: NfcWorkflowIdentity? = null

    private fun launchOrderDelegateAction(action: suspend NfcOrderWorkflowDelegate.() -> Unit) {
        if (orderActionJob?.isActive == true) return
        orderActionJob = viewModelScope.launch { orderDelegate.action() }
    }

    fun confirmEndOrder(params: EndOrderParams, request: suspend () -> LocationRequestResult = locationDelegate::acquireLocation) {
        if (params.orderKey != activeWorkflow?.orderKey ||
            (_uiState.value as? NfcSignInUiState.ShowConfirmDialog)?.endOrderParams != params) return
        launchOrderDelegateAction { confirmEndOrder(params, request) }
    }

    fun cancelEndOrder() {
        orderActionJob?.cancel()
        orderActionJob = null
        orderDelegate.cancelEndOrder()
    }

    fun resetState() = orderDelegate.resetState()

    fun showError(message: String) = orderDelegate.showError(message)

    fun buildServiceCompleteDataFromCache(
        orderKey: OrderKey,
        endOderInfo: EndOderInfo?,
        trueServiceTime: Int,
    ): ServiceCompleteData = orderDelegate.buildServiceCompleteDataFromCache(orderKey, endOderInfo, trueServiceTime)

    fun refreshExternalReaderReadyState() {
        if (_scanMode.value != ScanMode.EXTERNAL_RFID) return
        _readerUiState.value = initialExternalReaderUiState(externalRfidReaderManager.isReaderReady())
    }

    suspend fun acquireLocation(): LocationRequestResult = locationDelegate.acquireLocation()

    fun observeScanEvents(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> LocationRequestResult,
    ) {
        val identity = NfcWorkflowIdentity(orderKey, signInMode, endOderInfo)
        // A restored permission result can arrive before the new composition's launch effect.
        // Once detached, do not cancel a retry that already uses the new Activity callback.
        if (activeWorkflow != identity || scanDelegate.hasAttachedCallbacks) {
            orderActionJob?.cancel()
            orderActionJob = null
        }
        activeWorkflow = identity
        scanDelegate.observeScanEvents(orderKey, signInMode, endOderInfo, onLocationRequest)
    }

    fun resumePendingPermissionScan(onLocationRequest: suspend () -> LocationRequestResult): Boolean {
        if (activeWorkflow == null) return false
        if (orderDelegate.hasPendingEndLocation) {
            launchOrderDelegateAction { retryEndLocation(onLocationRequest) }
            return true
        }
        return scanDelegate.resumePendingPermissionScan(onLocationRequest)
    }

    fun clearPendingPermissionScan() {
        scanDelegate.clearPendingPermissionScan()
        orderDelegate.locationPermissionDenied()
    }

    fun stopScanRequests(preserveWorkflow: Boolean = false) {
        orderActionJob?.cancel()
        orderActionJob = null
        cancelR65cCompletionJob()
        r65cSessionCollector.reset()
        if (preserveWorkflow) {
            scanDelegate.detach()
        } else {
            activeWorkflow = null
            scanDelegate.clear()
        }
    }

    fun confirmLocationActivation(data: PendingNfcData, request: suspend () -> LocationRequestResult = locationDelegate::acquireLocation) =
        scanDelegate.confirmLocationActivation(data, request)

    fun retryLocation(request: suspend () -> LocationRequestResult) {
        if (activeWorkflow == null) return
        if (orderDelegate.hasPendingEndLocation) launchOrderDelegateAction { retryEndLocation(request) }
        else if (!scanDelegate.retryLocation(request)) resetState()
    }

    fun cancelLocationActivation() = scanDelegate.cancelLocationActivation()

    fun mockNfcScan(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
    ) = scanDelegate.mockNfcScan(orderKey, signInMode, endOderInfo)

    internal fun onR65cFallbackKeyEvent(event: R65cWorkflowHidCapturedKeyEvent) {
        if (_scanMode.value != ScanMode.EXTERNAL_RFID) return

        val completedPayload = r65cSessionCollector.onKeyEvent(event)
        if (completedPayload != null) {
            cancelR65cCompletionJob()
            submitR65cFallbackPayload(completedPayload)
            return
        }

        if (!r65cSessionCollector.hasPendingInput()) return

        _readerUiState.value = ReaderUiState.Reading
        cancelR65cCompletionJob()
        r65cCompletionJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400L)
            r65cSessionCollector.drainPending()?.let(::submitR65cFallbackPayload)
        }
    }

    private fun submitR65cFallbackPayload(rawPayload: String) {
        if (_scanMode.value != ScanMode.EXTERNAL_RFID) return
        _readerUiState.value = nextReaderUiStateAfterR65cFallbackSubmit(_readerUiState.value)
        if (rawPayload.isBlank()) return
        externalRfidReaderManager.submitHidCandidate(rawPayload)
    }

    private fun cancelR65cCompletionJob() {
        r65cCompletionJob?.cancel()
        r65cCompletionJob = null
    }

    override fun onCleared() {
        cancelR65cCompletionJob()
        r65cSessionCollector.reset()
        stopScanRequests()
    }
}

internal fun nextReaderUiStateAfterR65cFallbackSubmit(current: ReaderUiState): ReaderUiState {
    return if (current == ReaderUiState.Reading) ReaderUiState.Ready else current
}

internal fun initialExternalReaderUiState(isReaderReady: Boolean): ReaderUiState =
    if (isReaderReady) ReaderUiState.Ready else ReaderUiState.Disconnected
