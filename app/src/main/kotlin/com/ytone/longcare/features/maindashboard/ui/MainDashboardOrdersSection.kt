package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.features.maindashboard.vm.MainDashboardViewModel
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderItem
import com.ytone.longcare.features.shared.ui.EmptyView
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.handleOrderNavigation
import com.ytone.longcare.model.isPendingExecutionState
import com.ytone.longcare.theme.IndicatorGradientEnd
import com.ytone.longcare.theme.IndicatorGradientStart
import kotlinx.coroutines.launch

@Composable
fun InOrderServiceItem(
    order: ServiceOrderModel,
    onClick: () -> Unit = { }
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = order.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = stringResource(id = R.string.service_order_work_hours, order.planTotalTime),
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "地址: ${order.liveAddress}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "进入详情",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun OrderTabLayout(
    todayOrderList: List<TodayServiceOrderModel>,
    inOrderList: List<ServiceOrderModel>,
    actions: MainDashboardActions,
    homeSharedViewModel: HomeSharedViewModel,
    mainDashboardViewModel: MainDashboardViewModel
) {
    val selectedTabIndex by homeSharedViewModel.selectedTabIndex.collectAsStateWithLifecycle()
    val tabs = listOf("待护理计划", "服务中")
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
                    EmptyView(modifier = Modifier.height(376.dp), message = "暂无待护理计划")
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
                    EmptyView(modifier = Modifier.height(376.dp), message = "暂无服务中订单")
                }
            }
        }
    }
}

@Composable
fun CustomTabItem(
    text: String,
    isSelected: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF007AFF) else Color(0xFF999999),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (isSelected) {
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            val textWidthDp = with(density) { textLayoutResult.size.width.toDp() }

            Box(
                modifier = Modifier
                    .width(textWidthDp)
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                IndicatorGradientStart,
                                IndicatorGradientEnd
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(2.dp)
            )
        }
    }
}
