package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

    val result = orderRepository.checkOrder(
        orderKey.orderId,
        nfcDeviceId,
        location.longitude.toString(),
        location.latitude.toString()
    )
    currentCoroutineContext().ensureActive()
    when (result) {
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
    endType: Int,
    uiState: MutableStateFlow<NfcSignInUiState>,
    onCheckSuccess: suspend () -> Unit,
    userMessages: NfcUserMessages,
    diagnostics: EndOrderDiagnostics,
) {
    uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)
    val endOrderParams = createEndOrderParams(
        orderKey = orderKey,
        nfcDeviceId = nfcDeviceId,
        projectIdList = projectIdList,
        beginImgList = beginImgList,
        endImageList = endImageList,
        centerImgList = centerImgList,
        endType = endType
    )

    val checkResult = diagnostics.measure("check") {
        orderRepository.checkEndOrder(orderId = orderKey.orderId, projectIdList = projectIdList)
    }
    currentCoroutineContext().ensureActive()
    when (checkResult) {
        is ApiResult.Success -> onCheckSuccess()
        is ApiResult.Exception -> {
            diagnostics.finish("api_exception", throwable = checkResult.exception, isError = true)
            applyOrderApiException(
                exception = checkResult,
                uiState = uiState,
                userMessages = userMessages,
            )
        }
        is ApiResult.Failure -> {
            val needsConfirmation = checkResult.code == 3005
            diagnostics.finish(
                if (needsConfirmation) "confirmation_required" else "api_failure",
                failure = checkResult, isError = !needsConfirmation,
            )
            applyCheckEndOrderFailure(
                failure = checkResult,
                endOrderParams = endOrderParams,
                uiState = uiState,
            )
        }
    }
}
