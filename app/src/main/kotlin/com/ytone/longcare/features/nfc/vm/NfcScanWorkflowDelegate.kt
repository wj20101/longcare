package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
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
    private val orderDelegate: NfcOrderWorkflowDelegate,
) {
    private var nfcEventJob: Job? = null

    fun observeNfcEvents(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> LocationRequestResult
    ) {
        nfcEventJob?.cancel()
        nfcEventJob = scope.launch {
            appEventBus.events.collect { event ->
                if (event is AppEvent.NfcIntentReceived) {
                    handleNfcIntentReceived(
                        event = event,
                        currentState = uiState.value,
                        signInMode = signInMode,
                        endOderInfo = endOderInfo,
                        onLocationRequest = onLocationRequest,
                        onLocationError = { message -> orderDelegate.showError(message) },
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
                        }
                    )
                }
            }
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
                    orderDelegate.showError(result.exception.message ?: "绑定定位失败")
                }

                is ApiResult.Failure -> {
                    orderDelegate.showError(result.message)
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
        endOderInfo: EndOderInfo?
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
                }
            )
        }
    }

    fun clear() {
        nfcEventJob?.cancel()
    }
}
