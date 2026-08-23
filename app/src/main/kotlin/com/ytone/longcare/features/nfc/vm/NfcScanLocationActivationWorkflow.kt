package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal suspend fun checkUserLocationAndProceed(
    unifiedOrderRepository: OrderDetailRepository,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    longitude: String,
    latitude: String,
    pendingNfcData: MutableStateFlow<PendingNfcData?>,
    scope: CoroutineScope,
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
                        "hasLongitude" to longitude.isNotBlank(),
                        "hasLatitude" to latitude.isNotBlank(),
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
                        "hasLongitude" to longitude.isNotBlank(),
                        "hasLatitude" to latitude.isNotBlank(),
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
                        "hasLongitude" to longitude.isNotBlank(),
                        "hasLatitude" to latitude.isNotBlank(),
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
                        "hasLongitude" to longitude.isNotBlank(),
                        "hasLatitude" to latitude.isNotBlank(),
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
        longitude = longitude,
        latitude = latitude,
        pendingNfcData = pendingNfcData,
        scope = scope,
        orderDelegate = orderDelegate
    )
}

private fun checkLocationAndShowDialog(
    orderInfo: ServiceOrderInfoModel,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    longitude: String,
    latitude: String,
    pendingNfcData: MutableStateFlow<PendingNfcData?>,
    scope: CoroutineScope,
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
            longitude = longitude,
            latitude = latitude
        )
    } else {
        scope.launch {
            orderDelegate.startOrder(orderKey, tagId, longitude, latitude)
        }
    }
}
