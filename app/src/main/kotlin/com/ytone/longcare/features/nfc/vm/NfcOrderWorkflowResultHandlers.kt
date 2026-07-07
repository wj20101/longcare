package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.flow.MutableStateFlow

internal fun createEndOrderParams(
    orderKey: OrderKey,
    nfcDeviceId: String,
    projectIdList: List<Int>,
    beginImgList: List<String>,
    endImageList: List<String>,
    centerImgList: List<String>,
    longitude: String,
    latitude: String,
    endType: Int
): EndOrderParams {
    return EndOrderParams(
        orderKey = orderKey,
        nfcDeviceId = nfcDeviceId,
        porjectIdList = projectIdList,
        beginImgList = beginImgList,
        endImageList = endImageList,
        centerImgList = centerImgList,
        longitude = longitude,
        latitude = latitude,
        endType = endType
    )
}

internal fun applyOrderCheckSuccess(
    uiState: MutableStateFlow<NfcSignInUiState>
) {
    uiState.value = NfcSignInUiState.Success()
}

internal fun applyOrderApiException(
    exception: ApiResult.Exception,
    uiState: MutableStateFlow<NfcSignInUiState>
) {
    val message = exception.exception.message ?: "网络错误，请检查网络连接"
    uiState.value = reportedNfcError(message)
}

internal fun applyOrderApiFailure(
    failure: ApiResult.Failure,
    uiState: MutableStateFlow<NfcSignInUiState>
) {
    uiState.value = reportedNfcError(failure.message)
}

internal fun applyCheckEndOrderFailure(
    failure: ApiResult.Failure,
    endOrderParams: EndOrderParams,
    uiState: MutableStateFlow<NfcSignInUiState>
) {
    if (failure.code == 3005) {
        uiState.value = NfcSignInUiState.ShowConfirmDialog(
            message = failure.message,
            endOrderParams = endOrderParams
        )
        return
    }

    uiState.value = reportedNfcError(failure.message)
}
