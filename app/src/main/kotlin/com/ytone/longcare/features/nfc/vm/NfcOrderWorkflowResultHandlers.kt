package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.result.ApiResult
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
    uiState: MutableStateFlow<NfcSignInUiState>,
    userMessages: NfcUserMessages,
) {
    uiState.value = reportedNfcError(userMessages.networkError)
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
