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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.features.maindashboard.vm.MainDashboardViewModel
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.theme.bgGradientBrush

enum class ServiceOrderType {
    PENDING_CARE_PLANS,  // 待护理计划
    SERVICE_RECORDS      // 已服务记录
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceOrdersListScreen(
    navController: NavController,
    orderType: ServiceOrderType,
    viewModel: MainDashboardViewModel = hiltViewModel()
) {

    val todayOrderList by viewModel.todayOrderListState.collectAsStateWithLifecycle()
    
    // 根据类型过滤订单
    val filteredOrders = when (orderType) {
        ServiceOrderType.PENDING_CARE_PLANS -> todayOrderList.filter { it.state == 0 }
        ServiceOrderType.SERVICE_RECORDS -> todayOrderList.filter { it.state == 2 }
    }
    
    // 页面标题和空状态文案
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
    
    LaunchedEffect(Unit) {
        viewModel.loadTodayOrders()
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
                            text = title,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent
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
                                    text = emptySubtitle,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                } else {
                    items(filteredOrders) { order ->
                        ServiceOrderItem(
                            order = order,
                            orderType = orderType
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceOrderItem(
    order: TodayServiceOrderModel,
    orderType: ServiceOrderType
) {
    Card(
        onClick = { /*TODO: 导航到详情页*/ },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 根据订单类型显示不同的状态标签
                    when (orderType) {
                        ServiceOrderType.PENDING_CARE_PLANS -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE8F4FF)
                            ) {
                                Text(
                                    text = "工时: ${order.totalServiceTime}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        ServiceOrderType.SERVICE_RECORDS -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE8F5E8)
                            ) {
                                Text(
                                    text = "已完成",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE8F4FF)
                            ) {
                                Text(
                                    text = "工时: ${order.completeTotalTime}",
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
                    text = "地址: ${order.liveAddress}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (order.callPhone.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "联系电话: ${order.callPhone}",
                        fontSize = 12.sp,
                        color = Color.Gray
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

@Preview
@Composable
fun ServiceOrdersListScreenPreview() {
    LongCareTheme {
        ServiceOrdersListScreen(
            navController = rememberNavController(),
            orderType = ServiceOrderType.PENDING_CARE_PLANS
        )
    }
}