package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun performStartOrderWorkflow(
    orderRepository: OrderRepository,
    orderKey: OrderKey,
    nfcDeviceId: String,
    longitude: String,
    latitude: String,
    uiState: MutableStateFlow<NfcSignInUiState>,
    userMessages: NfcUserMessages,
) {
    uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)

    when (val result = orderRepository.checkOrder(
        orderKey.orderId,
        nfcDeviceId,
        longitude,
        latitude
    )) {
        is ApiResult.Success -> applyOrderCheckSuccess(uiState)
        is ApiResult.Exception -> {
            trackNfcException(
                event = "start_order_check_exception",
                description = "NFC开始工单校验异常",
                throwable = result.exception,
                orderKey = orderKey,
                signInMode = SignInMode.START_ORDER,
                nfcDeviceId = nfcDeviceId,
                extras = locationExtras(longitude, latitude),
            )
            applyOrderApiException(
                exception = result,
                uiState = uiState,
                userMessages = userMessages,
            )
        }
        is ApiResult.Failure -> {
            trackNfcFailure(
                event = "start_order_check_failure",
                description = "NFC开始工单校验业务失败",
                failure = result,
                orderKey = orderKey,
                signInMode = SignInMode.START_ORDER,
                nfcDeviceId = nfcDeviceId,
                extras = locationExtras(longitude, latitude),
            )
            applyOrderApiFailure(failure = result, uiState = uiState)
        }
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
    uiState: MutableStateFlow<NfcSignInUiState>,
    onCheckSuccess: suspend () -> Unit,
    userMessages: NfcUserMessages,
) {
    uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)
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
        is ApiResult.Exception -> {
            trackNfcException(
                event = "end_order_check_exception",
                description = "NFC结束工单校验异常",
                throwable = checkResult.exception,
                orderKey = orderKey,
                signInMode = SignInMode.END_ORDER,
                nfcDeviceId = nfcDeviceId,
                extras = locationExtras(longitude, latitude) + mapOf(
                    "projectCount" to projectIdList.size,
                    "beginImageCount" to beginImgList.size,
                    "centerImageCount" to centerImgList.size,
                    "endImageCount" to endImageList.size,
                    "endType" to endType,
                ),
            )
            applyOrderApiException(
                exception = checkResult,
                uiState = uiState,
                userMessages = userMessages,
            )
        }
        is ApiResult.Failure -> {
            trackNfcFailure(
                event = "end_order_check_failure",
                description = "NFC结束工单校验业务失败",
                failure = checkResult,
                orderKey = orderKey,
                signInMode = SignInMode.END_ORDER,
                nfcDeviceId = nfcDeviceId,
                extras = locationExtras(longitude, latitude) + mapOf(
                    "projectCount" to projectIdList.size,
                    "beginImageCount" to beginImgList.size,
                    "centerImageCount" to centerImgList.size,
                    "endImageCount" to endImageList.size,
                    "endType" to endType,
                ),
            )
            applyCheckEndOrderFailure(
                failure = checkResult,
                endOrderParams = endOrderParams,
                uiState = uiState,
            )
        }
    }
}

private fun locationExtras(longitude: String, latitude: String): Map<String, Any?> =
    mapOf(
        "hasLongitude" to longitude.isNotBlank(),
        "hasLatitude" to latitude.isNotBlank(),
    )
