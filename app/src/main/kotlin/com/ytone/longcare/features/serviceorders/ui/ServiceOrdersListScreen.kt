package com.ytone.longcare.features.serviceorders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.handleOrderNavigation
import com.ytone.longcare.model.isPendingCareState
import com.ytone.longcare.model.isServiceRecordState
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.theme.bgGradientBrush

enum class ServiceOrderType {
    PENDING_CARE_PLANS,  // 待护理计划
    SERVICE_RECORDS      // 已服务记录
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceOrdersListScreen(
    actions: ServiceOrdersListActions,
    orderType: ServiceOrderType,
    todayOrderViewModel: TodayOrderViewModel
) {
    val todayOrderList by todayOrderViewModel.todayOrderListState.collectAsStateWithLifecycle()

    // 统一处理系统返回键
    CustomBackHandler(customAction = actions.onNavigateBack)

    // 根据类型过滤订单
    val filteredOrders = when (orderType) {
        ServiceOrderType.PENDING_CARE_PLANS -> todayOrderList.filter { it.state.isPendingCareState() }
        ServiceOrderType.SERVICE_RECORDS -> todayOrderList.filter { it.state.isServiceRecordState() }
    }

    // 页面标题和空状态文案
    val (title, emptyTitle, emptySubtitle) = when (orderType) {
        ServiceOrderType.PENDING_CARE_PLANS -> Triple(
            "待护理计划", "暂无待护理计划", "当前没有需要执行的护理计划"
        )

        ServiceOrderType.SERVICE_RECORDS -> Triple(
            "已服务记录", "暂无服务记录", "当前没有已完成的服务记录"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                    Text(
                        text = title, fontWeight = FontWeight.Bold
                    )
                }, navigationIcon = {
                    IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
                )
            }, containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (filteredOrders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = emptyTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = emptySubtitle, fontSize = 14.sp, color = Color.Gray
                                )
                            }
                        }
                    }
                } else {
                    items(filteredOrders) { order ->
                        ServiceOrderItem(order = order, onClick = singleClick {
                    handleOrderNavigation(
                         state = order.state,
                         orderId = order.orderId,
                         planId = 0,
                         onNavigateToNursingExecution = { orderId, planId ->
                             actions.onNavigateToNursingExecution(OrderKey(orderId, planId))
                         },
                         onNavigateToService = { orderId, planId ->
                             actions.onNavigateToService(OrderKey(orderId, planId))
                         },
                         onNotStartedState = {
                             // 未开单状态，不允许跳转
                         }
                     )
                })
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun ServiceOrderItemPreview() {
    val order = TodayServiceOrderModel(name = "张三", state = 0, totalServiceTime = 60, liveAddress = "北京市朝阳区 xxx 街道 xxx 号", callPhone = "138xxxxxxxx")
    ServiceOrderItem(order = order)
}

@Composable
fun ServiceOrderItem(
    order: TodayServiceOrderModel, onClick: () -> Unit = { }
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 使用 FlowRow 让内容在大字体时自动换行
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = order.name, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )

                    // 根据state显示不同的状态标签
                    when (order.state) {
                        0 -> { // 待护理计划
                            Surface(
                                shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F4FF)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.service_order_work_hours, order.totalServiceTime),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        2 -> { // 已服务记录
                            Surface(
                                shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F5E8)
                            ) {
                                Text(
                                    text = "已完成",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F4FF)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.service_order_work_hours, order.completeTotalTime),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        else -> { // 其他状态，显示总工时
                            Surface(
                                shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F4FF)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.service_order_work_hours, order.totalServiceTime),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "地址: ${order.liveAddress}", fontSize = 12.sp, color = Color.Gray
                )
                if (order.callPhone.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "联系电话: ${order.callPhone}", fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = stringResource(R.string.common_details),
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
