package com.ytone.longcare.features.servicecountdown.ui

import android.content.Context
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
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
    context: Context,
    orderKey: OrderKey,
    projectIdList: List<Int>,
    countdownViewModel: ServiceCountdownViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    actions: ServiceCountdownActions,
    endType: Int
) {
    KLogger.w("NavigationDebug", "ServiceCountdownScreen: handleEndService called with endType=$endType")
    KLogger.i("ServiceCountdownScreen", "========================================")
    KLogger.i("ServiceCountdownScreen", "🛑 开始处理结束服务 (endType=$endType)...")
    KLogger.i("ServiceCountdownScreen", "========================================")

    CountdownForegroundService.stopCountdown(context)
    KLogger.i("ServiceCountdownScreen", "✅ 1. 已停止倒计时前台服务")

    locationTrackingViewModel.onStopClicked()
    KLogger.i("ServiceCountdownScreen", "✅ 2. 已停止定位跟踪服务")

    countdownViewModel.cancelCountdownAlarmForOrder(orderKey)
    KLogger.i("ServiceCountdownScreen", "✅ 3. 已取消倒计时闹钟 (orderId=${orderKey.orderId})")

    AlarmRingtoneService.stopRingtone(context)
    KLogger.i("ServiceCountdownScreen", "✅ 4. 已停止响铃服务")

    countdownViewModel.endServiceWithoutClearingImages(orderKey, context)
    KLogger.i("ServiceCountdownScreen", "✅ 5. 已结束服务（保留图片数据）")

    actions.onNavigateToEndServiceSelection(orderKey, endType, projectIdList)
}

internal fun handleOrderStateErrorAndExit(
    context: Context,
    orderKey: OrderKey,
    countdownViewModel: ServiceCountdownViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    actions: ServiceCountdownActions
) {
    KLogger.i("ServiceCountdownScreen", "========================================")
    KLogger.i("ServiceCountdownScreen", "🛑 开始处理订单状态异常，停止所有服务...")
    KLogger.i("ServiceCountdownScreen", "========================================")

    countdownViewModel.clearOrderStateError()
    KLogger.i("ServiceCountdownScreen", "✅ 1. 已清除错误状态")

    countdownViewModel.stopOrderStatePolling()
    KLogger.i("ServiceCountdownScreen", "✅ 2. 已停止订单状态轮询")

    CountdownForegroundService.stopCountdown(context)
    KLogger.i("ServiceCountdownScreen", "✅ 3. 已停止倒计时前台服务")

    locationTrackingViewModel.forceStop()
    KLogger.i("ServiceCountdownScreen", "✅ 4. 已强制停止定位跟踪服务")

    countdownViewModel.cancelCountdownAlarmForOrder(orderKey)
    KLogger.i("ServiceCountdownScreen", "✅ 5. 已取消倒计时闹钟 (orderId=${orderKey.orderId})")

    AlarmRingtoneService.stopRingtone(context)
    KLogger.i("ServiceCountdownScreen", "✅ 6. 已停止响铃服务")

    countdownViewModel.endServiceWithoutClearingImages(orderKey, context)
    KLogger.i("ServiceCountdownScreen", "✅ 7. 已清理ViewModel状态")

    KLogger.i("ServiceCountdownScreen", "========================================")
    KLogger.i("ServiceCountdownScreen", "✅ 所有服务已停止，准备返回首页")
    KLogger.i("ServiceCountdownScreen", "========================================")

    actions.onNavigateHomeAndClearStack()
}
