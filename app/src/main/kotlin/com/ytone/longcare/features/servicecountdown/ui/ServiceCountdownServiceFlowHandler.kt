package com.ytone.longcare.features.servicecountdown.ui

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel

internal fun buildOrderStateErrorMessage(stateModel: ServiceOrderStateModel): String {
    return when (stateModel.state) {
        ServiceOrderStateModel.STATE_NOT_CREATED -> "订单未开单，无法继续服务"
        ServiceOrderStateModel.STATE_PENDING -> "订单状态异常：待执行"
        ServiceOrderStateModel.STATE_COMPLETED -> "订单已完成，无法继续服务"
        ServiceOrderStateModel.STATE_CANCELLED -> "订单已作废，无法继续服务"
        else -> stateModel.stateDesc ?: "订单状态异常，无法继续服务"
    }
}

internal fun handleEndService(
    orderKey: OrderKey,
    projectIdList: List<Int>,
    countdownViewModel: ServiceCountdownViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    actions: ServiceCountdownActions,
    endType: Int
) {
    locationTrackingViewModel.onStopClicked()
    countdownViewModel.endServiceWithoutClearingImages(orderKey)
    KLogger.i("ServiceCountdownScreen", "服务已结束并保留图片: orderId=${orderKey.orderId}, endType=$endType")

    actions.onNavigateToEndServiceSelection(orderKey, endType, projectIdList)
}

internal fun handleOrderStateErrorAndExit(
    orderKey: OrderKey,
    countdownViewModel: ServiceCountdownViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    actions: ServiceCountdownActions
) {
    countdownViewModel.clearOrderStateError()
    locationTrackingViewModel.forceStop()
    countdownViewModel.endServiceWithoutClearingImages(orderKey)
    KLogger.i("ServiceCountdownScreen", "订单状态异常，运行资源已统一停止: orderId=${orderKey.orderId}")

    actions.onNavigateHomeAndClearStack()
}
