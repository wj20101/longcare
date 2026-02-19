package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun executeEndOrderRequest(
    orderRepository: OrderRepository,
    toastHelper: ToastHelper,
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
            toastHelper.showShort(message)
            uiState.value = NfcSignInUiState.Error(message)
        }

        is ApiResult.Failure -> {
            toastHelper.showShort(result.message)
            uiState.value = NfcSignInUiState.Error(result.message)
        }
    }
}
