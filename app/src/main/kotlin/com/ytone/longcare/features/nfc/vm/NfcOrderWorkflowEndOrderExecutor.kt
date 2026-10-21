package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun executeEndOrderRequest(
    serviceOrderLifecycle: ServiceOrderLifecycle,
    orderRepository: OrderRepository,
    completionDelegate: NfcOrderCompletionDelegate,
    uiState: MutableStateFlow<NfcSignInUiState>,
    orderKey: OrderKey,
    nfcDeviceId: String,
    projectIdList: List<Int>,
    beginImgList: List<String>,
    endImageList: List<String>,
    centerImgList: List<String>,
    location: LocationResult,
    endType: Int,
    userMessages: NfcUserMessages,
    diagnostics: EndOrderDiagnostics = EndOrderDiagnostics(orderKey, "submit"),
) {
    // Capture sample age at dispatch, rather than adding network latency to the evidence.
    diagnostics.captureLocation(location)
    val result = diagnostics.measure("submit") {
        orderRepository.endOrder(
            orderId = orderKey.orderId,
            nfcDeviceId = nfcDeviceId,
            projectIdList = projectIdList,
            beginImgList = beginImgList,
            centerImgList = centerImgList,
            endImageList = endImageList,
            longitude = location.longitude.toString(),
            latitude = location.latitude.toString(),
            endType = endType
        )
    }
    currentCoroutineContext().ensureActive()
    when (result) {
        is ApiResult.Success -> {
            val cleanedUp = diagnostics.measure("completion") {
                serviceOrderLifecycle.onOrderEnded(orderKey.orderId)
                completionDelegate.cleanupResources(orderKey)
            }
            uiState.value = NfcSignInUiState.Success(
                endOrderSuccessData = EndOrderSuccessData(
                    trueServiceTime = result.data.trueServiceTime
                )
            )
            diagnostics.finish(if (cleanedUp) "success" else "success_cleanup_failed")
        }

        is ApiResult.Exception -> {
            diagnostics.finish("api_exception", throwable = result.exception, isError = true)
            uiState.value = reportedNfcError(userMessages.networkError)
        }

        is ApiResult.Failure -> {
            diagnostics.finish("api_failure", failure = result, isError = true)
            uiState.value = reportedNfcError(result.message)
        }
    }
}
