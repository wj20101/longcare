package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.network.ApiResult
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
    orderDelegate: NfcOrderWorkflowDelegate
) {
    val orderInfo = unifiedOrderRepository.getCachedOrderInfo(orderKey)
        ?: when (val result = unifiedOrderRepository.getOrderInfo(orderKey)) {
            is ApiResult.Success -> result.data
            is ApiResult.Exception -> {
                orderDelegate.showError("获取订单详情失败: ${result.exception.message ?: "网络异常"}")
                return
            }

            is ApiResult.Failure -> {
                orderDelegate.showError("获取订单详情失败: ${result.message}")
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
