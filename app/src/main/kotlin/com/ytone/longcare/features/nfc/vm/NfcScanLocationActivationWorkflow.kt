package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun checkUserLocationAndProceed(
    unifiedOrderRepository: OrderDetailRepository,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    location: LocationResult,
    pendingNfcData: MutableStateFlow<PendingNfcData?>,
    orderDelegate: NfcOrderWorkflowDelegate,
    userMessages: NfcUserMessages,
) {
    val orderInfo = unifiedOrderRepository.getCachedOrderInfo(orderKey)
        ?: when (val result = unifiedOrderRepository.getOrderInfo(orderKey)) {
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
                        "hasLongitude" to location.longitude.isFinite(),
                        "hasLatitude" to location.latitude.isFinite(),
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
                        "hasLongitude" to location.longitude.isFinite(),
                        "hasLatitude" to location.latitude.isFinite(),
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
                        "hasLongitude" to location.longitude.isFinite(),
                        "hasLatitude" to location.latitude.isFinite(),
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
                        "hasLongitude" to location.longitude.isFinite(),
                        "hasLatitude" to location.latitude.isFinite(),
                    ),
                )
                return
            }
        }

    checkLocationAndShowDialog(
        orderInfo = orderInfo,
        orderKey = orderKey,
        signInMode = signInMode,
        endOderInfo = endOderInfo,
        tagId = tagId,
        location = location,
        pendingNfcData = pendingNfcData,
        orderDelegate = orderDelegate
    )
}

private suspend fun checkLocationAndShowDialog(
    orderInfo: ServiceOrderInfoModel,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    location: LocationResult,
    pendingNfcData: MutableStateFlow<PendingNfcData?>,
    orderDelegate: NfcOrderWorkflowDelegate
) {
    val userLng = orderInfo.userInfo?.lng ?: ""
    val userLat = orderInfo.userInfo?.lat ?: ""

    if (userLng.isEmpty() || userLat.isEmpty()) {
        pendingNfcData.value = PendingNfcData(
            orderKey = orderKey,
            signInMode = signInMode,
            endOderInfo = endOderInfo,
            tagId = tagId,
            location = location
        )
    } else {
        orderDelegate.startOrder(orderKey, tagId, location)
    }
}
