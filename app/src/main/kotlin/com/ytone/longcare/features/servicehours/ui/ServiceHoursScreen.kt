package com.ytone.longcare.features.servicehours.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.shared.vm.OrderDetailViewModel
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceHoursScreen(
    navController: NavController, orderId: Long, viewModel: OrderDetailViewModel = hiltViewModel()
) {

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    // 获取UI状态
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 页面初始化时获取订单详情
    LaunchedEffect(orderId) {
        viewModel.getOrderInfo(orderId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        when (val state = uiState) {
            is OrderDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is OrderDetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "错误: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is OrderDetailUiState.Success -> {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    state.orderInfo.userInfo.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                val address = state.orderInfo.userInfo.address
                                if (address.isNotBlank()){
                                    Text(
                                        "地址: $address",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }, navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back)
                                )
                            }
                        }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                        )
                    }, containerColor = Color.Transparent
                ) { paddingValues ->
                    // 使用 Box 实现 ServiceHoursTag 叠加在 ServiceRecordList 之上
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues) // 应用来自Scaffold的padding
                    ) {
                        // 列表内容区域，需要给顶部留出空间给 ServiceHoursTag
                        ServiceRecordList(
                            projects = state.orderInfo.projectList,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 18.dp) // 为 ServiceHoursTag 预留空间，可调整
                        )

                        // "已服务工时"标签，通过 offset 和对齐方式进行叠加
                        ServiceHoursTag(
                            modifier = Modifier.padding(start = 16.dp), tagText = "已服务工时"
                        )
                    }
                }
            }

            is OrderDetailUiState.Initial -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "正在初始化...", color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceRecordList(
    projects: List<ServiceProjectM>, modifier: Modifier = Modifier
) {

    // 白色背景和顶部圆角的容器
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp) // 列表容器左右外边距
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(projects) { index, project ->
                ServiceRecordItem(project)
                if (index < projects.lastIndex) { // 不是最后一项才添加分割线
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = Color(0xFFF0F0F0)
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceRecordItem(project: ServiceProjectM) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = project.projectName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "工时: ${project.serviceTime}", color = Color.Gray, fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "服务时间: ${project.lastServiceTime}", color = Color.Gray, fontSize = 13.sp
            )
        }
    }
}

// --- 预览 ---
@Preview(showBackground = true)
@Composable
fun ServiceHoursScreenPreview() {
    MaterialTheme {
        ServiceHoursScreen(navController = rememberNavController(), orderId = 1L)
    }
}