package com.ytone.longcare.features.servicehours.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.servicehours.api.ServiceHoursActions
import com.ytone.longcare.shared.vm.OrderDetailViewModel
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.model.OrderKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceHoursScreen(
    actions: ServiceHoursActions,
    orderKey: OrderKey,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    // 获取选中的项目ID
    val selectedProjectIds by viewModel.selectedProjectIds.collectAsStateWithLifecycle()

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================

    // 获取UI状态
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 统一处理系统返回键
    CustomBackHandler(customAction = actions.onNavigateBack)

    // 页面初始化时获取订单详情
    LaunchedEffect(orderKey) {
        viewModel.getOrderInfo(orderKey)
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
                                        state.orderInfo.userInfo?.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    val address = state.orderInfo.userInfo?.address ?: ""
                                    if (address.isNotBlank()) {
                                        Text(
                                            "地址: $address",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }, navigationIcon = {
                                IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.common_back)
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
                    // 使用 Box 实现 ServiceHoursTag 叠加在 ServiceRecordList 之上
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues) // 应用来自Scaffold的padding
                    ) {
                        // 列表内容区域，需要给顶部留出空间给 ServiceHoursTag
                        val selectedProjects = getSelectedProjects(
                            allProjects = state.orderInfo.projectList ?: emptyList(),
                            selectedProjectIds = selectedProjectIds
                        )
                        ServiceRecordList(
                            projects = selectedProjects,
                            orderInfo = state.orderInfo,
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
