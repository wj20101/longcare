package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class NfcScanWorkflowDelegate(
    private val appEventBus: AppEventBus,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val orderRepository: OrderRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<NfcSignInUiState>,
    private val pendingNfcData: MutableStateFlow<PendingNfcData?>,
    private val scanMode: MutableStateFlow<ScanMode>,
    private val readerUiState: MutableStateFlow<ReaderUiState>,
    private val orderDelegate: NfcOrderWorkflowDelegate,
) {
    private var nfcEventJob: Job? = null
    private var pendingPermissionScan: PendingNfcScan? = null

    fun observeScanEvents(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> LocationRequestResult,
    ) {
        nfcEventJob?.cancel()
        nfcEventJob = scope.launch {
            appEventBus.events.collect { event ->
                readerUiState.value = reduceReaderUiState(
                    currentMode = scanMode.value,
                    event = event,
                    currentReaderState = readerUiState.value,
                )

                if (event is AppEvent.TagScanned && event.isFromActiveSource(scanMode.value)) {
                    handleTagScanned(
                        event = event,
                        currentState = uiState.value,
                        signInMode = signInMode,
                        endOderInfo = endOderInfo,
                        onLocationRequest = onLocationRequest,
                        onLocationError = { error ->
                            orderDelegate.showError(
                                message = error.message,
                                source = "scan_location_error",
                                orderKey = orderKey,
                                signInMode = signInMode,
                                buglyAlreadyReported = error.buglyReported,
                            )
                        },
                        onLoadingReasonChanged = { reason ->
                            uiState.value = NfcSignInUiState.Loading(reason)
                        },
                        onLocationPermissionRequired = { tagId ->
                            pendingPermissionScan = PendingNfcScan(
                                orderKey = orderKey,
                                signInMode = signInMode,
                                endOderInfo = endOderInfo,
                                tagId = tagId
                            )
                        },
                        onStartOrder = { tagId, longitude, latitude ->
                            checkUserLocationAndProceed(
                                unifiedOrderRepository = unifiedOrderRepository,
                                orderKey = orderKey,
                                signInMode = signInMode,
                                endOderInfo = endOderInfo,
                                tagId = tagId,
                                longitude = longitude,
                                latitude = latitude,
                                pendingNfcData = pendingNfcData,
                                scope = scope,
                                orderDelegate = orderDelegate
                            )
                        },
                        onEndOrder = { tagId, longitude, latitude, info ->
                            orderDelegate.endOrder(
                                orderKey = orderKey,
                                nfcDeviceId = tagId,
                                projectIdList = info.projectIdList,
                                beginImgList = info.beginImgList,
                                centerImgList = info.centerImgList,
                                endImageList = info.endImgList,
                                longitude = longitude,
                                latitude = latitude,
                                endType = info.endType
                            )
                        },
                    )
                }
            }
        }
    }

    fun resumePendingPermissionScan(onLocationRequest: suspend () -> LocationRequestResult): Boolean {
        val scan = pendingPermissionScan ?: return false
        pendingPermissionScan = null
        scope.launch {
            uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.FETCHING_LOCATION)
            val locationResult = onLocationRequest()
            val (longitude, latitude) = when (locationResult) {
                is LocationRequestResult.Coordinates -> locationResult.longitude to locationResult.latitude
                is LocationRequestResult.Error -> {
                    orderDelegate.showError(
                        message = locationResult.message,
                        source = "resume_permission_scan_location_error",
                        orderKey = scan.orderKey,
                        signInMode = scan.signInMode,
                        nfcDeviceId = scan.tagId,
                        buglyAlreadyReported = locationResult.buglyReported,
                    )
                    return@launch
                }
                is LocationRequestResult.PermissionRequired -> return@launch
            }

            executeSignInModeAction(
                signInMode = scan.signInMode,
                endOderInfo = scan.endOderInfo,
                tagId = scan.tagId,
                longitude = longitude,
                latitude = latitude,
                onStartOrder = { tagId, startLongitude, startLatitude ->
                    checkUserLocationAndProceed(
                        unifiedOrderRepository = unifiedOrderRepository,
                        orderKey = scan.orderKey,
                        signInMode = scan.signInMode,
                        endOderInfo = scan.endOderInfo,
                        tagId = tagId,
                        longitude = startLongitude,
                        latitude = startLatitude,
                        pendingNfcData = pendingNfcData,
                        scope = scope,
                        orderDelegate = orderDelegate
                    )
                },
                onEndOrder = { tagId, endLongitude, endLatitude, info ->
                    orderDelegate.endOrder(
                        orderKey = scan.orderKey,
                        nfcDeviceId = tagId,
                        projectIdList = info.projectIdList,
                        beginImgList = info.beginImgList,
                        centerImgList = info.centerImgList,
                        endImageList = info.endImgList,
                        longitude = endLongitude,
                        latitude = endLatitude,
                        endType = info.endType
                    )
                },
            )
        }
        return true
    }

    fun clearPendingPermissionScan() {
        pendingPermissionScan = null
        if ((uiState.value as? NfcSignInUiState.Loading)?.reason ==
            NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION
        ) {
            uiState.value = NfcSignInUiState.Initial
        }
    }

    fun confirmLocationActivation(data: PendingNfcData) {
        scope.launch {
            when (val result = orderRepository.bindLocation(
                orderId = data.orderKey.orderId,
                nfc = data.tagId,
                longitude = data.longitude,
                latitude = data.latitude
            )) {
                is ApiResult.Success -> {
                    orderDelegate.startOrder(data.orderKey, data.tagId, data.longitude, data.latitude)
                }

                is ApiResult.Exception -> {
                    val message = result.exception.message ?: "绑定定位失败，请检查网络后重试"
                    trackNfcException(
                        event = "bind_location_exception",
                        description = "NFC绑定定位异常",
                        throwable = result.exception,
                        orderKey = data.orderKey,
                        signInMode = data.signInMode,
                        nfcDeviceId = data.tagId,
                        extras = mapOf(
                            "hasLongitude" to data.longitude.isNotBlank(),
                            "hasLatitude" to data.latitude.isNotBlank(),
                        ),
                    )
                    orderDelegate.showError(
                        message = message,
                        source = "bind_location",
                        orderKey = data.orderKey,
                        signInMode = data.signInMode,
                        nfcDeviceId = data.tagId,
                        buglyAlreadyReported = true,
                        extras = mapOf(
                            "hasLongitude" to data.longitude.isNotBlank(),
                            "hasLatitude" to data.latitude.isNotBlank(),
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    trackNfcFailure(
                        event = "bind_location_failure",
                        description = "NFC绑定定位业务失败",
                        failure = result,
                        orderKey = data.orderKey,
                        signInMode = data.signInMode,
                        nfcDeviceId = data.tagId,
                        extras = mapOf(
                            "hasLongitude" to data.longitude.isNotBlank(),
                            "hasLatitude" to data.latitude.isNotBlank(),
                        ),
                    )
                    orderDelegate.showError(
                        message = result.message,
                        source = "bind_location",
                        orderKey = data.orderKey,
                        signInMode = data.signInMode,
                        nfcDeviceId = data.tagId,
                        buglyAlreadyReported = true,
                        extras = mapOf(
                            "hasLongitude" to data.longitude.isNotBlank(),
                            "hasLatitude" to data.latitude.isNotBlank(),
                        ),
                    )
                }
            }
            pendingNfcData.value = null
        }
    }

    fun cancelLocationActivation() {
        pendingNfcData.value = null
    }

    fun mockNfcScan(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
    ) {
        val mockTagId = "MOCK_TAG_ID_123456"
        val mockLongitude = "121.4737"
        val mockLatitude = "31.2304"

        scope.launch {
            executeSignInModeAction(
                signInMode = signInMode,
                endOderInfo = endOderInfo,
                tagId = mockTagId,
                longitude = mockLongitude,
                latitude = mockLatitude,
                onStartOrder = { tagId, longitude, latitude ->
                    checkUserLocationAndProceed(
                        unifiedOrderRepository = unifiedOrderRepository,
                        orderKey = orderKey,
                        signInMode = signInMode,
                        endOderInfo = endOderInfo,
                        tagId = tagId,
                        longitude = longitude,
                        latitude = latitude,
                        pendingNfcData = pendingNfcData,
                        scope = scope,
                        orderDelegate = orderDelegate
                    )
                },
                onEndOrder = { tagId, longitude, latitude, info ->
                    orderDelegate.endOrder(
                        orderKey = orderKey,
                        nfcDeviceId = tagId,
                        projectIdList = info.projectIdList,
                        beginImgList = info.beginImgList,
                        centerImgList = info.centerImgList,
                        endImageList = info.endImgList,
                        longitude = longitude,
                        latitude = latitude,
                        endType = info.endType
                    )
                },
            )
        }
    }

    fun clear() {
        nfcEventJob?.cancel()
        pendingPermissionScan = null
    }

    private fun AppEvent.TagScanned.isFromActiveSource(currentMode: ScanMode): Boolean = when (currentMode) {
        ScanMode.SYSTEM_NFC -> source == ScanSource.SYSTEM_NFC
        ScanMode.EXTERNAL_RFID -> source == ScanSource.EXTERNAL_RFID
    }
}
