package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.flow.MutableStateFlow

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
) {
    val userId = DiagnosticEventTracker.currentUserId()
    val locationFields = nfcLocationExtras(location)
    val diagnosticFields = endOrderDiagnosticExtras(locationFields, projectIdList, beginImgList, centerImgList, endImageList, endType)
    klogI(
        "executeEndOrder: Begin: ${beginImgList.size}, Center: ${centerImgList.size}, End: ${endImageList.size}",
    )

    when (val result = orderRepository.endOrder(
        orderId = orderKey.orderId,
        nfcDeviceId = nfcDeviceId,
        projectIdList = projectIdList,
        beginImgList = beginImgList,
        centerImgList = centerImgList,
        endImageList = endImageList,
        longitude = location.longitude.toString(),
        latitude = location.latitude.toString(),
        endType = endType
    )) {
        is ApiResult.Success -> {
            trackNfcLocation("nfc_location_submit_success", orderKey, SignInMode.END_ORDER, "end_submit", location, userId = userId, locationFields = locationFields)
            serviceOrderLifecycle.onOrderEnded(orderKey.orderId)
            completionDelegate.cleanupResources(orderKey)
            uiState.value = NfcSignInUiState.Success(
                endOrderSuccessData = EndOrderSuccessData(
                    trueServiceTime = result.data.trueServiceTime
                )
            )
        }

        is ApiResult.Exception -> {
            trackNfcException(
                event = "end_order_submit_exception",
                description = "NFC结束工单提交异常",
                throwable = result.exception,
                orderKey = orderKey,
                signInMode = SignInMode.END_ORDER,
                nfcDeviceId = nfcDeviceId,
                userId = userId,
                extras = diagnosticFields,
            )
            uiState.value = reportedNfcError(userMessages.networkError)
        }

        is ApiResult.Failure -> {
            trackNfcFailure(
                event = "end_order_submit_failure",
                description = "NFC结束工单提交业务失败",
                failure = result,
                orderKey = orderKey,
                signInMode = SignInMode.END_ORDER,
                nfcDeviceId = nfcDeviceId,
                userId = userId,
                extras = diagnosticFields,
            )
            uiState.value = reportedNfcError(result.message)
        }
    }
}

private fun endOrderDiagnosticExtras(
    locationFields: Map<String, Any?>,
    projectIdList: List<Int>,
    beginImgList: List<String>,
    centerImgList: List<String>,
    endImageList: List<String>,
    endType: Int,
): Map<String, Any?> =
    locationFields + mapOf(
        "projectCount" to projectIdList.size,
        "beginImageCount" to beginImgList.size,
        "centerImageCount" to centerImgList.size,
        "endImageCount" to endImageList.size,
        "endType" to endType,
    )
