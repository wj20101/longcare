package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun performStartOrderWorkflow(
    orderRepository: OrderRepository,
    orderKey: OrderKey,
    nfcDeviceId: String,
    location: LocationResult,
    uiState: MutableStateFlow<NfcSignInUiState>,
    userMessages: NfcUserMessages,
) {
    val userId = DiagnosticEventTracker.currentUserId()
    val locationFields = nfcLocationExtras(location)
    uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)

    when (val result = orderRepository.checkOrder(
        orderKey.orderId,
        nfcDeviceId,
        location.longitude.toString(),
        location.latitude.toString()
    )) {
        is ApiResult.Success -> {
            trackNfcLocation("nfc_location_submit_success", orderKey, SignInMode.START_ORDER, "start_check", location, userId = userId, locationFields = locationFields)
            applyOrderCheckSuccess(uiState)
        }
        is ApiResult.Exception -> {
            trackNfcException(
                event = "start_order_check_exception",
                description = "NFC开始工单校验异常",
                throwable = result.exception,
                orderKey = orderKey,
                signInMode = SignInMode.START_ORDER,
                nfcDeviceId = nfcDeviceId,
                userId = userId,
                extras = locationFields,
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
                userId = userId,
                extras = locationFields,
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
    location: LocationResult,
    endType: Int,
    uiState: MutableStateFlow<NfcSignInUiState>,
    onCheckSuccess: suspend () -> Unit,
    userMessages: NfcUserMessages,
) {
    val userId = DiagnosticEventTracker.currentUserId()
    val locationFields = nfcLocationExtras(location)
    uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)
    val endOrderParams = createEndOrderParams(
        orderKey = orderKey,
        nfcDeviceId = nfcDeviceId,
        projectIdList = projectIdList,
        beginImgList = beginImgList,
        endImageList = endImageList,
        centerImgList = centerImgList,
        location = location,
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
                userId = userId,
                extras = locationFields + mapOf(
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
                userId = userId,
                extras = locationFields + mapOf(
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
