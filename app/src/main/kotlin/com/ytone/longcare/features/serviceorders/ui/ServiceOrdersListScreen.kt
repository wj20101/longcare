package com.ytone.longcare.features.serviceorders.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.core.ui.message.UiMessageSnackbarEffect
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
    val uiMessages by todayOrderViewModel.uiMessages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    UiMessageSnackbarEffect(
        messages = uiMessages,
        snackbarHostState = snackbarHostState,
        onConsumed = todayOrderViewModel::consumeUiMessage,
    )

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

    Box(modifier = Modifier.fillMaxSize()) {
        ServiceOrdersListScreenLayout(
            title = title,
            emptyTitle = emptyTitle,
            emptySubtitle = emptySubtitle,
            filteredOrders = filteredOrders,
            actions = actions
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
