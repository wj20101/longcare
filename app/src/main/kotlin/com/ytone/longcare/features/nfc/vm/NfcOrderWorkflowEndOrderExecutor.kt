package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun executeEndOrderRequest(
    orderRepository: OrderRepository,
    completionDelegate: NfcOrderCompletionDelegate,
    uiState: MutableStateFlow<NfcSignInUiState>,
    orderKey: OrderKey,
    nfcDeviceId: String,
    projectIdList: List<Int>,
    beginImgList: List<String>,
    endImageList: List<String>,
    centerImgList: List<String>,
    longitude: String,
    latitude: String,
    endType: Int
) {
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
        longitude = longitude,
        latitude = latitude,
        endType = endType
    )) {
        is ApiResult.Success -> {
            completionDelegate.cleanupResources(orderKey)
            uiState.value = NfcSignInUiState.Success(
                endOrderSuccessData = EndOrderSuccessData(
                    trueServiceTime = result.data.trueServiceTime
                )
            )
        }

        is ApiResult.Exception -> {
            val message = result.exception.message ?: "网络错误，请检查网络连接"
            trackNfcException(
                event = "end_order_submit_exception",
                description = "NFC结束工单提交异常",
                throwable = result.exception,
                orderKey = orderKey,
                signInMode = SignInMode.END_ORDER,
                nfcDeviceId = nfcDeviceId,
                extras = endOrderDiagnosticExtras(
                    longitude = longitude,
                    latitude = latitude,
                    projectIdList = projectIdList,
                    beginImgList = beginImgList,
                    centerImgList = centerImgList,
                    endImageList = endImageList,
                    endType = endType,
                ),
            )
            uiState.value = reportedNfcError(message)
        }

        is ApiResult.Failure -> {
            trackNfcFailure(
                event = "end_order_submit_failure",
                description = "NFC结束工单提交业务失败",
                failure = result,
                orderKey = orderKey,
                signInMode = SignInMode.END_ORDER,
                nfcDeviceId = nfcDeviceId,
                extras = endOrderDiagnosticExtras(
                    longitude = longitude,
                    latitude = latitude,
                    projectIdList = projectIdList,
                    beginImgList = beginImgList,
                    centerImgList = centerImgList,
                    endImageList = endImageList,
                    endType = endType,
                ),
            )
            uiState.value = reportedNfcError(result.message)
        }
    }
}

private fun endOrderDiagnosticExtras(
    longitude: String,
    latitude: String,
    projectIdList: List<Int>,
    beginImgList: List<String>,
    centerImgList: List<String>,
    endImageList: List<String>,
    endType: Int,
): Map<String, Any?> =
    mapOf(
        "hasLongitude" to longitude.isNotBlank(),
        "hasLatitude" to latitude.isNotBlank(),
        "projectCount" to projectIdList.size,
        "beginImageCount" to beginImgList.size,
        "centerImageCount" to centerImgList.size,
        "endImageCount" to endImageList.size,
        "endType" to endType,
    )
