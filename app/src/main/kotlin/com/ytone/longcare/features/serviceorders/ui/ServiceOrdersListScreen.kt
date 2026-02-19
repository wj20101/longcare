package com.ytone.longcare.features.serviceorders.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.model.isPendingCareState
import com.ytone.longcare.model.isServiceRecordState
import com.ytone.longcare.shared.vm.TodayOrderViewModel

enum class ServiceOrderType {
    PENDING_CARE_PLANS,
    SERVICE_RECORDS
}

@Composable
fun ServiceOrdersListScreen(
    actions: ServiceOrdersListActions,
    orderType: ServiceOrderType,
    todayOrderViewModel: TodayOrderViewModel
) {
    val todayOrderList by todayOrderViewModel.todayOrderListState.collectAsStateWithLifecycle()

    CustomBackHandler(customAction = actions.onNavigateBack)

    val filteredOrders = when (orderType) {
        ServiceOrderType.PENDING_CARE_PLANS -> todayOrderList.filter { it.state.isPendingCareState() }
        ServiceOrderType.SERVICE_RECORDS -> todayOrderList.filter { it.state.isServiceRecordState() }
    }

    val (title, emptyTitle, emptySubtitle) = when (orderType) {
        ServiceOrderType.PENDING_CARE_PLANS -> Triple(
            "待护理计划",
            "暂无待护理计划",
            "当前没有需要执行的护理计划"
        )

        ServiceOrderType.SERVICE_RECORDS -> Triple(
            "已服务记录",
            "暂无服务记录",
            "当前没有已完成的服务记录"
        )
    }

    ServiceOrdersListScreenLayout(
        title = title,
        emptyTitle = emptyTitle,
        emptySubtitle = emptySubtitle,
        filteredOrders = filteredOrders,
        actions = actions
    )
}
