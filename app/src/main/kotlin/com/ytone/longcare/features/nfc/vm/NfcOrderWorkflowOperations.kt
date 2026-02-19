package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun performStartOrderWorkflow(
    orderRepository: OrderRepository,
    orderKey: OrderKey,
    nfcDeviceId: String,
    longitude: String,
    latitude: String,
    toastHelper: ToastHelper,
    uiState: MutableStateFlow<NfcSignInUiState>
) {
    uiState.value = NfcSignInUiState.Loading

    when (val result = orderRepository.checkOrder(
        orderKey.orderId,
        nfcDeviceId,
        longitude,
        latitude
    )) {
        is ApiResult.Success -> applyOrderCheckSuccess(uiState)
        is ApiResult.Exception -> applyOrderApiException(
            exception = result,
            toastHelper = toastHelper,
            uiState = uiState
        )
        is ApiResult.Failure -> applyOrderApiFailure(
            failure = result,
            toastHelper = toastHelper,
            uiState = uiState
        )
    }
}

internal suspend fun performEndOrderWorkflow(
    orderRepository: OrderRepository,
    orderKey: OrderKey,
    nfcDeviceId: String,
    projectIdList: List<Int>,
    beginImgList: List<String>,
    endImageList: List<String>,
    centerImgList: List<String>,
    longitude: String,
    latitude: String,
    endType: Int,
    toastHelper: ToastHelper,
    uiState: MutableStateFlow<NfcSignInUiState>,
    onCheckSuccess: suspend () -> Unit
) {
    uiState.value = NfcSignInUiState.Loading
    val endOrderParams = createEndOrderParams(
        orderKey = orderKey,
        nfcDeviceId = nfcDeviceId,
        projectIdList = projectIdList,
        beginImgList = beginImgList,
        endImageList = endImageList,
        centerImgList = centerImgList,
        longitude = longitude,
        latitude = latitude,
        endType = endType
    )

    when (val checkResult = orderRepository.checkEndOrder(
        orderId = orderKey.orderId,
        projectIdList = projectIdList
    )) {
        is ApiResult.Success -> onCheckSuccess()
        is ApiResult.Exception -> applyOrderApiException(
            exception = checkResult,
            toastHelper = toastHelper,
            uiState = uiState
        )
        is ApiResult.Failure -> applyCheckEndOrderFailure(
            failure = checkResult,
            endOrderParams = endOrderParams,
            toastHelper = toastHelper,
            uiState = uiState
        )
    }
}
