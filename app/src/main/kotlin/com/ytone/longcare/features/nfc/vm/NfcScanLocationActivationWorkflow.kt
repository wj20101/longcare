package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun checkUserLocationAndProceed(
    unifiedOrderRepository: OrderDetailRepository,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    pendingNfcData: MutableStateFlow<PendingNfcData?>,
    orderDelegate: NfcOrderWorkflowDelegate,
    userMessages: NfcUserMessages,
    onLocationNeeded: suspend () -> Unit,
) {
    val orderInfo = unifiedOrderRepository.getCachedOrderInfo(orderKey)
        ?: run {
            val result = unifiedOrderRepository.getOrderInfo(orderKey)
            // 详情失败也可能晚到；旧订单不能覆盖新订单的提示和重试状态。
            currentCoroutineContext().ensureActive()
            when (result) {
                is ApiResult.Success -> result.data
                is ApiResult.Exception -> {
                    trackNfcException(
                        event = "location_activation_order_detail_exception",
                        description = "NFC绑定定位前获取订单详情异常",
                        throwable = result.exception,
                        orderKey = orderKey,
                        signInMode = signInMode,
                        nfcDeviceId = tagId,
                        extras = mapOf(
                            "locationNotRequested" to true,
                        ),
                    )
                    orderDelegate.showError(
                        message = userMessages.orderDetailLoadFailed,
                        source = "location_activation_order_detail",
                        orderKey = orderKey,
                        signInMode = signInMode,
                        nfcDeviceId = tagId,
                        buglyAlreadyReported = true,
                        extras = mapOf(
                            "locationNotRequested" to true,
                        ),
                    )
                    return
                }

                is ApiResult.Failure -> {
                    trackNfcFailure(
                        event = "location_activation_order_detail_failure",
                        description = "NFC绑定定位前获取订单详情业务失败",
                        failure = result,
                        orderKey = orderKey,
                        signInMode = signInMode,
                        nfcDeviceId = tagId,
                        extras = mapOf(
                            "locationNotRequested" to true,
                        ),
                    )
                    orderDelegate.showError(
                        message = result.message.ifBlank { userMessages.orderDetailLoadFailed },
                        source = "location_activation_order_detail",
                        orderKey = orderKey,
                        signInMode = signInMode,
                        nfcDeviceId = tagId,
                        buglyAlreadyReported = true,
                        extras = mapOf(
                            "locationNotRequested" to true,
                        ),
                    )
                    return
                }
            }
        }

    currentCoroutineContext().ensureActive()
    checkLocationAndShowDialog(
        orderInfo = orderInfo,
        orderKey = orderKey,
        signInMode = signInMode,
        endOderInfo = endOderInfo,
        tagId = tagId,
        pendingNfcData = pendingNfcData,
        onLocationNeeded = onLocationNeeded,
    )
}

private suspend fun checkLocationAndShowDialog(
    orderInfo: ServiceOrderInfoModel,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    pendingNfcData: MutableStateFlow<PendingNfcData?>,
    onLocationNeeded: suspend () -> Unit,
) {
    val userLng = orderInfo.userInfo?.lng ?: ""
    val userLat = orderInfo.userInfo?.lat ?: ""

    if (userLng.isEmpty() || userLat.isEmpty()) {
        pendingNfcData.value = PendingNfcData(
            orderKey = orderKey,
            signInMode = signInMode,
            endOderInfo = endOderInfo,
            tagId = tagId,
        )
    } else {
        onLocationNeeded()
    }
}
