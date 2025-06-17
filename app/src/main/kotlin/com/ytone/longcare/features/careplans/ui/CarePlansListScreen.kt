package com.ytone.longcare.features.careplans.ui

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarePlansListScreen(
    navController: NavController,
    viewModel: MainDashboardViewModel = hiltViewModel()
) {
    val todayOrderList by viewModel.todayOrderListState.collectAsStateWithLifecycle()
    
    // 过滤出待护理计划 (state == 0)
    val carePlans = todayOrderList.filter { it.state == 0 }
    
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
                            text = "待护理计划",
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
                if (carePlans.isEmpty()) {
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
                                    text = "暂无待护理计划",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "当前没有需要执行的护理计划",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                } else {
                    items(carePlans) { plan ->
                        CarePlanItem(plan = plan)
                    }
                }
            }
        }
    }
}

@Composable
fun CarePlanItem(plan: TodayServiceOrderModel) {
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
                        text = plan.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFE8F4FF)
                    ) {
                        Text(
                            text = "工时: ${plan.totalServiceTime}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "地址: ${plan.liveAddress}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (plan.callPhone.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "联系电话: ${plan.callPhone}",
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
fun CarePlansListScreenPreview() {
    LongCareTheme {
        CarePlansListScreen(navController = rememberNavController())
    }
}