package com.ytone.longcare.features.servicecountdown.ui

import android.content.Context
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel

internal fun buildOrderStateErrorMessage(
    context: Context,
    stateModel: ServiceOrderStateModel,
): String {
    return when (stateModel.state) {
        ServiceOrderStateModel.STATE_NOT_CREATED -> context.getString(R.string.service_countdown_order_not_created)
        ServiceOrderStateModel.STATE_PENDING -> context.getString(R.string.service_countdown_order_pending)
        ServiceOrderStateModel.STATE_COMPLETED -> context.getString(R.string.service_countdown_order_completed)
        ServiceOrderStateModel.STATE_CANCELLED -> context.getString(R.string.service_countdown_order_cancelled)
        else -> stateModel.stateDesc
            ?: context.getString(R.string.service_countdown_order_unknown_state)
    }
}

internal fun handleEndService(
    orderKey: OrderKey,
    projectIdList: List<Int>,
    actions: ServiceCountdownActions,
    endType: Int
) {
    actions.onNavigateToEndServiceSelection(orderKey, endType, projectIdList)
}

internal fun handleOrderStateErrorAndExit(
    orderKey: OrderKey,
    countdownViewModel: ServiceCountdownViewModel,
    actions: ServiceCountdownActions
) {
    countdownViewModel.clearOrderStateError()
    countdownViewModel.onOrderEnded(orderKey)
    KLogger.i("ServiceCountdownScreen", "订单状态异常，运行资源已统一停止: orderId=${orderKey.orderId}")

    actions.onNavigateHomeAndClearStack()
}
