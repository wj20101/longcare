package com.ytone.longcare.features.home.api

import com.ytone.longcare.core.ui.message.UiMessage
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow view of the app-owned, HomeGraph-scoped order state used by the care dashboard.
 *
 * Implementations must forward the existing owner rather than create a second cache or duplicate
 * order business rules inside Home.
 */
interface HomeOrderStateSource {
    val todayOrders: StateFlow<List<TodayServiceOrderModel>>
    val inProgressOrders: StateFlow<List<ServiceOrderModel>>
    val messages: StateFlow<List<UiMessage>>

    fun refreshTodayOrders()

    fun refreshInProgressOrders()

    fun consumeMessage(id: Long)
}
