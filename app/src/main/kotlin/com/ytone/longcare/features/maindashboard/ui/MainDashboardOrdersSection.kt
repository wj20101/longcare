package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.R
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.features.maindashboard.vm.MainDashboardViewModel
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderItem
import com.ytone.longcare.features.shared.ui.EmptyView
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.handleOrderNavigation
import com.ytone.longcare.model.isPendingExecutionState
import kotlinx.coroutines.launch

@Composable
fun OrderTabLayout(
    todayOrderList: List<TodayServiceOrderModel>,
    inOrderList: List<ServiceOrderModel>,
    actions: MainDashboardActions,
    homeSharedViewModel: HomeSharedViewModel,
    mainDashboardViewModel: MainDashboardViewModel
) {
    val selectedTabIndex by homeSharedViewModel.selectedTabIndex.collectAsStateWithLifecycle()
    val tabs = listOf(
        stringResource(R.string.dashboard_pending_care_plans),
        stringResource(R.string.dashboard_in_service),
    )
    val coroutineScope = rememberCoroutineScope()

    Column {
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            divider = {},
            indicator = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { homeSharedViewModel.updateSelectedTabIndex(index) },
                    text = {
                        CustomTabItem(
                            text = title,
                            isSelected = selectedTabIndex == index
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTabIndex) {
            0 -> {
                val pendingOrders = todayOrderList.filter { it.state.isPendingExecutionState() }
                if (pendingOrders.isNotEmpty()) {
                    pendingOrders.forEach { order ->
                        ServiceOrderItem(order = order) {
                            handleOrderNavigation(
                                state = order.state,
                                orderId = order.orderId,
                                planId = 0,
                                onNavigateToNursingExecution = actions.onNavigateToNursingExecution,
                                onNavigateToService = actions.onNavigateToService,
                                onNotStartedState = {
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    EmptyView(
                        modifier = Modifier.height(376.dp),
                        message = stringResource(R.string.dashboard_empty_pending_care_plans),
                    )
                }
            }

            1 -> {
                if (inOrderList.isNotEmpty()) {
                    inOrderList.forEach { order ->
                        InOrderServiceItem(order = order) {
                            coroutineScope.launch {
                                try {
                                    val navigationData =
                                        mainDashboardViewModel.buildServiceCountdownNavigationData(
                                            orderId = order.orderId,
                                            planId = 0
                                        )
                                    if (navigationData == null) {
                                        logE("跳转到服务倒计时页面失败: orderId=${order.orderId}, navigationData=null")
                                        return@launch
                                    }

                                    actions.onNavigateToServiceCountdown(
                                        navigationData.orderKey,
                                        navigationData.projectIdList
                                    )
                                } catch (e: Exception) {
                                    logE("跳转到服务倒计时页面失败: orderId=${order.orderId}", throwable = e)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    EmptyView(
                        modifier = Modifier.height(376.dp),
                        message = stringResource(R.string.dashboard_empty_in_service),
                    )
                }
            }
        }
    }
}
