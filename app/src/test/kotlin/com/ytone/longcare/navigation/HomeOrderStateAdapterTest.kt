package com.ytone.longcare.navigation

import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.core.ui.message.UiMessage
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class HomeOrderStateAdapterTest {

    @Test
    fun `adapter forwards the graph owner flows and commands without copying state`() {
        val todayOrders = MutableStateFlow<List<TodayServiceOrderModel>>(emptyList())
        val inProgressOrders = MutableStateFlow<List<ServiceOrderModel>>(emptyList())
        val messages = MutableStateFlow<List<UiMessage>>(emptyList())
        val viewModel = mockk<TodayOrderViewModel>()
        every { viewModel.todayOrderListState } returns todayOrders
        every { viewModel.inOrderListState } returns inProgressOrders
        every { viewModel.uiMessages } returns messages
        every { viewModel.loadTodayOrders() } just runs
        every { viewModel.loadInOrders() } just runs
        every { viewModel.consumeUiMessage(any()) } just runs

        val source = viewModel.asHomeOrderStateSource()
        source.refreshTodayOrders()
        source.refreshInProgressOrders()
        source.consumeMessage(19L)

        assertThat(source.todayOrders).isSameInstanceAs(todayOrders)
        assertThat(source.inProgressOrders).isSameInstanceAs(inProgressOrders)
        assertThat(source.messages).isSameInstanceAs(messages)
        verify(exactly = 1) { viewModel.loadTodayOrders() }
        verify(exactly = 1) { viewModel.loadInOrders() }
        verify(exactly = 1) { viewModel.consumeUiMessage(19L) }
    }
}
