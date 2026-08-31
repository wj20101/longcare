package com.ytone.longcare.navigation

import com.ytone.longcare.core.ui.message.UiMessage
import com.ytone.longcare.features.home.api.HomeOrderStateSource
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import kotlinx.coroutines.flow.StateFlow

/** Adapts the existing HomeGraph-owned ViewModel without introducing another order cache. */
internal class TodayOrderHomeStateSource(
    private val viewModel: TodayOrderViewModel,
) : HomeOrderStateSource {
    override val todayOrders: StateFlow<List<TodayServiceOrderModel>>
        get() = viewModel.todayOrderListState

    override val inProgressOrders: StateFlow<List<ServiceOrderModel>>
        get() = viewModel.inOrderListState

    override val messages: StateFlow<List<UiMessage>>
        get() = viewModel.uiMessages

    override fun refreshTodayOrders() = viewModel.loadTodayOrders()

    override fun refreshInProgressOrders() = viewModel.loadInOrders()

    override fun consumeMessage(id: Long) = viewModel.consumeUiMessage(id)
}

internal fun TodayOrderViewModel.asHomeOrderStateSource(): HomeOrderStateSource =
    TodayOrderHomeStateSource(this)
