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
    val info = readCurrentNfcOrderInfo(unifiedOrderRepository, orderKey, signInMode, tagId,
        orderDelegate, userMessages) ?: return
    if (info.hasBoundLocation()) onLocationNeeded()
    else pendingNfcData.value = PendingNfcData(orderKey, signInMode, endOderInfo, tagId)
}

internal fun ServiceOrderInfoModel.hasBoundLocation(): Boolean =
    !userInfo?.lng.isNullOrBlank() && !userInfo?.lat.isNullOrBlank()

/** Binding is server state, not a decision that can use a page's pre-bind detail cache. */
internal suspend fun readCurrentNfcOrderInfo(
    repository: OrderDetailRepository,
    orderKey: OrderKey,
    signInMode: SignInMode,
    tagId: String,
    orderDelegate: NfcOrderWorkflowDelegate,
    userMessages: NfcUserMessages,
): ServiceOrderInfoModel? {
    val result = repository.getOrderInfo(orderKey, forceRefresh = true)
    currentCoroutineContext().ensureActive()
    when (result) {
        is ApiResult.Success -> return result.data
        is ApiResult.Exception -> trackNfcException(
            event = "location_activation_order_detail_exception",
            description = "NFC绑定定位前获取订单详情异常",
            throwable = result.exception,
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = tagId,
            extras = mapOf("locationNotRequested" to true),
        )
        is ApiResult.Failure -> trackNfcFailure(
            event = "location_activation_order_detail_failure",
            description = "NFC绑定定位前获取订单详情业务失败",
            failure = result,
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = tagId,
            extras = mapOf("locationNotRequested" to true),
        )
    }
    orderDelegate.showError(
        message = (result as? ApiResult.Failure)?.message?.ifBlank { userMessages.orderDetailLoadFailed }
            ?: userMessages.orderDetailLoadFailed,
        source = "location_activation_order_detail",
        orderKey = orderKey,
        signInMode = signInMode,
        nfcDeviceId = tagId,
        buglyAlreadyReported = true,
        extras = mapOf("locationNotRequested" to true),
    )
    return null
}
