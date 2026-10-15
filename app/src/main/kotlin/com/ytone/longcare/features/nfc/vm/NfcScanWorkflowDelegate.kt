package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class NfcScanWorkflowDelegate(
    private val locationFacade: LocationFacade,
    private val acquireLocation: suspend () -> LocationRequestResult,
    private val appEventBus: AppEventBus,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val orderRepository: OrderRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<NfcSignInUiState>,
    private val pendingNfcData: MutableStateFlow<PendingNfcData?>,
    private val scanMode: MutableStateFlow<ScanMode>,
    private val readerUiState: MutableStateFlow<ReaderUiState>,
    private val orderDelegate: NfcOrderWorkflowDelegate,
    private val userMessages: NfcUserMessages,
) {
    private var nfcEventJob: Job? = null
    private var pendingActionJob: Job? = null
    private var pendingPermissionScan: PendingNfcScan? = null
    private var retryScan: PendingNfcScan? = null
    private var retryBinding: PendingNfcData? = null
    private var confirmedBindingOrder: OrderKey? = null

    fun observeScanEvents(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> LocationRequestResult,
    ) {
        clear()
        nfcEventJob = scope.launch {
            appEventBus.events.collect { event ->
                readerUiState.value = reduceReaderUiState(scanMode.value, event, readerUiState.value)
                if (event is AppEvent.TagScanned && event.isFromActiveSource(scanMode.value) &&
                    event.tagId.isNotBlank() && uiState.value is NfcSignInUiState.Initial && pendingNfcData.value == null
                ) {
                    processScan(PendingNfcScan(orderKey, signInMode, endOderInfo, event.tagId), onLocationRequest)
                }
            }
        }
    }

    private suspend fun processScan(scan: PendingNfcScan, request: suspend () -> LocationRequestResult) {
        if (scan.signInMode == SignInMode.END_ORDER) {
            retryScan = null
            val info = scan.endOderInfo ?: return
            orderDelegate.endOrder(scan.orderKey, scan.tagId, info.projectIdList, info.beginImgList,
                info.endImgList, info.centerImgList, info.endType, request)
            return
        }
        retryScan = scan
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)
        // 先读客户资料；未绑定时先确认，再采集，避免页面等待消耗有效定位。
        if (confirmedBindingOrder == scan.orderKey) {
            requestAndStart(scan, request)
        } else {
            checkUserLocationAndProceed(unifiedOrderRepository, scan.orderKey, scan.signInMode,
                scan.endOderInfo, scan.tagId, pendingNfcData, orderDelegate, userMessages,
                onLocationNeeded = { requestAndStart(scan, request) })
        }
        currentCoroutineContext().ensureActive()
        if (pendingNfcData.value != null) uiState.value = NfcSignInUiState.Initial
    }

    private suspend fun requestAndStart(scan: PendingNfcScan, request: suspend () -> LocationRequestResult) {
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.CARD_RECOGNIZED_FETCHING_LOCATION)
        when (val result = requestNfcLocation(scan.orderKey, scan.signInMode, "scan", userMessages.locationUnavailable, request)) {
            is LocationRequestResult.Coordinates -> {
                currentCoroutineContext().ensureActive()
                // 提交前复核失败仍可重试定位；已尝试业务请求则不自动重放。
                if (orderDelegate.startOrder(scan.orderKey, scan.tagId, result.location)) retryScan = null
            }
            is LocationRequestResult.Error -> orderDelegate.showError(result.message, buglyAlreadyReported = result.buglyReported)
            LocationRequestResult.PermissionRequired -> waitForPermission(scan)
        }
    }

    fun retryLocation(request: suspend () -> LocationRequestResult): Boolean {
        val binding = retryBinding
        val scan = retryScan ?: binding?.let { PendingNfcScan(it.orderKey, it.signInMode, it.endOderInfo, it.tagId) }
            ?: return false
        if (pendingActionJob?.isActive == true) return true
        pendingActionJob = scope.launch {
            if (binding != null) bindCurrentLocation(binding, request) else processScan(scan, request)
        }
        return true
    }

    fun resumePendingPermissionScan(onLocationRequest: suspend () -> LocationRequestResult): Boolean {
        if (pendingPermissionScan == null) return false
        pendingPermissionScan = null
        return retryLocation(onLocationRequest)
    }

    private fun waitForPermission(scan: PendingNfcScan) {
        pendingPermissionScan = scan
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION)
    }

    fun clearPendingPermissionScan() {
        pendingPermissionScan?.let {
            trackNfcLocation("nfc_location_permission_denied", it.orderKey, it.signInMode, "permission")
        }
        pendingPermissionScan = null
        if ((uiState.value as? NfcSignInUiState.Loading)?.reason == NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION) {
            // 保留 retryScan/retryBinding，用户授权后可直接重试，无需再次碰卡。
            orderDelegate.showLocationFailure(LocationFailure.PERMISSION)
        }
    }

    fun confirmLocationActivation(data: PendingNfcData, request: suspend () -> LocationRequestResult = acquireLocation) {
        if (pendingNfcData.value != data || pendingActionJob?.isActive == true) return
        pendingNfcData.value = null
        retryBinding = data
        retryScan = null
        pendingActionJob = scope.launch { bindCurrentLocation(data, request) }
    }

    private suspend fun bindCurrentLocation(data: PendingNfcData, request: suspend () -> LocationRequestResult) {
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.FETCHING_LOCATION)
        val result = requestNfcLocation(data.orderKey, data.signInMode, "bind_submit_acquire", userMessages.locationUnavailable, request)
        currentCoroutineContext().ensureActive()
        val location = when (result) {
            is LocationRequestResult.Coordinates -> result.location
            is LocationRequestResult.Error -> {
                orderDelegate.showError(result.message, buglyAlreadyReported = result.buglyReported)
                return
            }
            LocationRequestResult.PermissionRequired -> {
                waitForPermission(PendingNfcScan(data.orderKey, data.signInMode, data.endOderInfo, data.tagId))
                return
            }
        }
        val failure = locationFacade.serviceOrderLocationFailure(location)
        if (failure != null) {
            orderDelegate.showLocationFailure(failure)
            return
        }
        val userId = DiagnosticEventTracker.currentUserId()
        val locationFields = nfcLocationExtras(location)
        trackNfcLocation("nfc_location_validated", data.orderKey, data.signInMode, "bind_submit", location,
            userId = userId, locationFields = locationFields)
        // 清除自动重试动作；网络结果未知时必须重新核对客户绑定状态。
        retryBinding = null
        val response = orderRepository.bindLocation(data.orderKey.orderId, data.tagId,
            location.longitude.toString(), location.latitude.toString())
        currentCoroutineContext().ensureActive()
        when (response) {
            is ApiResult.Success -> {
                trackNfcLocation("nfc_location_submit_success", data.orderKey, data.signInMode, "bind_submit", location,
                    userId = userId, locationFields = locationFields)
                // 绑定网络请求可能很慢；签到重新取点，后续定位重试不能再次绑定客户。
                confirmedBindingOrder = data.orderKey
                val scan = PendingNfcScan(data.orderKey, data.signInMode, data.endOderInfo, data.tagId)
                retryScan = scan
                requestAndStart(scan, request)
            }
            is ApiResult.Exception -> {
                trackNfcException("bind_location_exception", "NFC绑定定位异常", response.exception,
                    data.orderKey, data.signInMode, data.tagId, extras = locationFields, userId = userId)
                orderDelegate.showError(userMessages.bindLocationFailed, buglyAlreadyReported = true)
            }
            is ApiResult.Failure -> {
                trackNfcFailure("bind_location_failure", "NFC绑定定位业务失败", response,
                    data.orderKey, data.signInMode, data.tagId, extras = locationFields, userId = userId)
                orderDelegate.showError(response.message, buglyAlreadyReported = true)
            }
        }
    }

    fun cancelLocationActivation() {
        pendingNfcData.value = null
        retryBinding = null
        retryScan = null
        pendingActionJob?.cancel()
        uiState.value = NfcSignInUiState.Initial
    }

    fun mockNfcScan(orderKey: OrderKey, signInMode: SignInMode, endOderInfo: EndOderInfo?) {
        pendingActionJob?.cancel()
        pendingActionJob = scope.launch {
            processScan(PendingNfcScan(orderKey, signInMode, endOderInfo, "MOCK_TAG_ID_123456"), acquireLocation)
        }
    }

    fun clear() {
        nfcEventJob?.cancel()
        pendingActionJob?.cancel()
        pendingNfcData.value = null
        pendingPermissionScan = null
        retryScan = null
        retryBinding = null
        confirmedBindingOrder = null
        orderDelegate.resetState()
    }

    private fun AppEvent.TagScanned.isFromActiveSource(mode: ScanMode): Boolean = when (mode) {
        ScanMode.SYSTEM_NFC -> source == ScanSource.SYSTEM_NFC
        ScanMode.EXTERNAL_RFID -> source == ScanSource.EXTERNAL_RFID
    }
}
