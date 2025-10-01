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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.UnifiedBackHandler
import com.ytone.longcare.shared.vm.OrderDetailViewModel
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.common.utils.SelectedProjectsManager
import com.ytone.longcare.api.request.OrderInfoRequestModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceHoursScreen(
    navController: NavController, 
    orderInfoRequest: OrderInfoRequestModel, 
    viewModel: OrderDetailViewModel = hiltViewModel(),
    selectedProjectsManager: SelectedProjectsManager
) {
    val orderId = orderInfoRequest.orderId

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    // 获取UI状态
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 统一处理系统返回键
    UnifiedBackHandler(navController = navController)

    // 页面初始化时获取订单详情
    LaunchedEffect(orderInfoRequest) {
        viewModel.getOrderInfo(orderInfoRequest)
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
                                IconButton(onClick = { navController.popBackStack() }) {
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
                            selectedProjectsManager = selectedProjectsManager,
                            orderId = orderId
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

/**
 * 获取选中的项目列表
 * @param allProjects 所有项目列表
 * @param selectedProjectsManager 选中项目管理器
 * @param orderId 订单ID
 * @return 过滤后的项目列表
 */
fun getSelectedProjects(
    allProjects: List<ServiceProjectM>,
    selectedProjectsManager: SelectedProjectsManager,
    orderId: Long
): List<ServiceProjectM> {
    val selectedProjectIds = selectedProjectsManager.getSelectedProjects(orderId)
    
    return if (selectedProjectIds?.isNotEmpty() == true) {
        // 根据选中的项目ID过滤项目列表
        allProjects.filter { project -> 
            selectedProjectIds.contains(project.projectId) 
        }
    } else {
        // 如果没有选中项目数据，返回所有项目（兼容性处理）
        allProjects
    }
}

@Composable
fun ServiceRecordList(
    projects: List<ServiceProjectM>,
    orderInfo: ServiceOrderInfoModel,
    modifier: Modifier = Modifier
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
                ServiceRecordItem(project, orderInfo)
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

@Preview
@Composable
fun ServiceRecordListPreview() {
    val sampleProjects = listOf(
        ServiceProjectM(
            projectId = 1,
            projectName = "日常清洁",
            serviceTime = 60,
            lastServiceTime = "2023-10-26 10:00"
        ),
        ServiceProjectM(
            projectId = 2,
            projectName = "健康监测",
            serviceTime = 30,
            lastServiceTime = "2023-10-26 11:30"
        ),
        ServiceProjectM(
            projectId = 3,
            projectName = "助浴服务",
            serviceTime = 90,
            lastServiceTime = "2023-10-25 14:00"
        )
    )
    val sampleOrderInfo = ServiceOrderInfoModel(
        startTime = "09:00",
        endTime = "12:00"
    )
    MaterialTheme {
        ServiceRecordList(projects = sampleProjects, orderInfo = sampleOrderInfo)
    }
}


@Composable
fun ServiceRecordItem(project: ServiceProjectM, orderInfo: ServiceOrderInfoModel) {
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
                    text = stringResource(id = R.string.service_order_work_hours, project.serviceTime), color = Color.Gray, fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val serviceTimeText = if (orderInfo.startTime.isNotBlank() && orderInfo.endTime.isNotBlank()) {
                "服务时间: ${orderInfo.startTime} - ${orderInfo.endTime}"
            } else if (orderInfo.startTime.isNotBlank()) {
                "开始时间: ${orderInfo.startTime}"
            } else {
                "服务时间: 未设置"
            }
            Text(
                text = serviceTimeText, color = Color.Gray, fontSize = 13.sp
            )
        }
    }
}

@Preview
@Composable
fun ServiceRecordItemPreview() {
    val sampleProject = ServiceProjectM(
        projectId = 1,
        projectName = "助餐服务",
        serviceTime = 45,
        lastServiceTime = "2023-10-27 12:00"
    )
    val sampleOrderInfo = ServiceOrderInfoModel(
        startTime = "09:00",
        endTime = "12:00"
    )
    MaterialTheme {
        ServiceRecordItem(project = sampleProject, orderInfo = sampleOrderInfo)
    }
}