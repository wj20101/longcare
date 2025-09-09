package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.UnifiedBackHandler
import com.ytone.longcare.di.SelectServiceEntryPoint
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.shared.vm.StarOrderUiState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.api.request.OrderInfoRequestModel

// --- 数据模型 ---
data class ServiceItem(
    val id: Int, val name: String, val duration: Int, // 分钟
    var isSelected: Boolean = false
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectServiceScreen(
    navController: NavController, 
    orderInfoRequest: OrderInfoRequestModel, 
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val selectedProjectsManager = EntryPointAccessors.fromApplication(
        context.applicationContext,
        SelectServiceEntryPoint::class.java
    ).selectedProjectsManager()
    
    val navigationHelper = EntryPointAccessors.fromApplication(
        context.applicationContext,
        SelectServiceEntryPoint::class.java
    ).navigationHelper()
    // 使用SharedViewModel获取订单详情
    val uiState by sharedViewModel.uiState.collectAsState()
    val starOrderState by sharedViewModel.starOrderState.collectAsStateWithLifecycle()

    // 统一处理系统返回键
    UnifiedBackHandler(navController = navController)

    // 在组件初始化时加载订单信息（如果缓存中没有）
    LaunchedEffect(orderInfoRequest) {
        // 先检查缓存，如果没有缓存数据才请求
        if (sharedViewModel.getCachedOrderInfo(orderInfoRequest) == null) {
            sharedViewModel.getOrderInfo(orderInfoRequest)
        } else {
            // 如果有缓存数据，直接设置为成功状态
            sharedViewModel.getOrderInfo(orderInfoRequest, forceRefresh = false)
        }
    }

    // 监听starOrder状态，成功后执行路由跳转
    LaunchedEffect(starOrderState) {
        if (starOrderState is StarOrderUiState.Success) {
            // 重置状态
            sharedViewModel.resetStarOrderState()
        }
    }

    // 根据API返回的数据转换为UI需要的ServiceItem格式
    val serviceItems = remember { mutableStateListOf<ServiceItem>() }

    // 当uiState变化时更新serviceItems
    LaunchedEffect(uiState) {
        when (val currentState = uiState) {
            is OrderDetailUiState.Success -> {
                serviceItems.clear()
                serviceItems.addAll(
                    (currentState.orderInfo.projectList ?: emptyList()).map { project ->
                        ServiceItem(
                            id = project.projectId,
                            name = project.projectName,
                            duration = project.serviceTime,
                            isSelected = false // 默认不选中，用户可以手动选择
                        )
                    })
            }

            else -> {
                serviceItems.clear()
            }
        }
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
                            "请选择服务项目", fontWeight = FontWeight.Bold
                        )
                    }, navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                TotalDurationDisplay(totalDuration = serviceItems.filter { it.isSelected }
                    .sumOf { it.duration })

                Spacer(modifier = Modifier.height(24.dp))

                // 根据UI状态显示不同内容
                when (val currentState = uiState) {
                    is OrderDetailUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }

                    is OrderDetailUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "加载失败: ${currentState.message}",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }

                    is OrderDetailUiState.Success -> {
                        ServiceSelectionList(
                            serviceItems = serviceItems, onItemClick = { clickedIndex ->
                                // 创建一个新的列表副本并修改选中状态，以触发 recomposition
                                val currentItem = serviceItems[clickedIndex]
                                serviceItems[clickedIndex] =
                                    currentItem.copy(isSelected = !currentItem.isSelected)
                            })
                    }

                    is OrderDetailUiState.Initial -> {
                        // 初始状态，显示空白或占位符
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "正在初始化...", color = Color.White, fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 底部按钮行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 全选按钮
                    SelectAllButton(
                        isAllSelected = serviceItems.isNotEmpty() && serviceItems.all { it.isSelected },
                        onClick = {
                            val isAllSelected = serviceItems.all { it.isSelected }
                            for (i in serviceItems.indices) {
                                serviceItems[i] = serviceItems[i].copy(isSelected = !isAllSelected)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 下一步按钮
                    NextStepButton(
                        text = "开始服务",
                        enabled = serviceItems.any { it.isSelected } && starOrderState !is StarOrderUiState.Loading,
                        onClick = {
                            val selectedProjectIds =
                                serviceItems.filter { it.isSelected }.map { it.id }
                            // 先调用starOrder接口
                            sharedViewModel.starOrder(orderInfoRequest.orderId, selectedProjectIds.map { it.toLong() }) {
                                // 成功后保存选中的项目ID到本地存储
                                selectedProjectsManager.saveSelectedProjects(orderInfoRequest.orderId, selectedProjectIds)
                                // 从uiState获取orderInfo
                                val currentState = uiState
                                if (currentState is OrderDetailUiState.Success) {
                                    // 使用NavigationHelper统一处理跳转逻辑
                                    navigationHelper.navigateToServiceCountdownWithLogic(
                                        navController = navController,
                                        orderId = orderInfoRequest.orderId,
                                        projectList = currentState.orderInfo.projectList ?: emptyList(),
                                        selectedProjectIds = selectedProjectIds
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun TotalDurationDisplay(totalDuration: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${totalDuration}分钟",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "本次服务总工时", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun ServiceSelectionList(
    serviceItems: List<ServiceItem>, onItemClick: (Int) -> Unit, modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp) // 列表项之间的间距
    ) {
        itemsIndexed(serviceItems) { index, item ->
            ServiceSelectionItem(
                item = item, onClick = { onItemClick(index) })
        }
    }
}

@Composable
fun ServiceSelectionItem(item: ServiceItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${item.duration}分钟",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            // 自定义勾选图标
            Icon(
                imageVector = if (item.isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (item.isSelected) stringResource(R.string.common_selected) else stringResource(
                    R.string.common_unselected
                ),
                tint = if (item.isSelected) Color(0xFF34C759) else Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun NextStepButton(
    text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF5CA0FF), Color(0xFF2A8CFF)) // 根据设计图调整渐变色
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(50.dp)
            .background(brush = buttonGradient, shape = CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Gray, // 确保禁用时渐变也可见 (或设置特定禁用渐变)
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview
@Composable
fun TotalDurationDisplayPreview() {
    TotalDurationDisplay(totalDuration = 120)
}

@Preview
@Composable
fun ServiceSelectionListPreview() {
    val serviceItems = listOf(
        ServiceItem(id = 1, name = "基础护理", duration = 60, isSelected = true),
        ServiceItem(id = 2, name = "康复训练", duration = 45),
        ServiceItem(id = 3, name = "心理疏导", duration = 30, isSelected = false)
    )
    ServiceSelectionList(serviceItems = serviceItems, onItemClick = {})
}

@Preview
@Composable
fun ServiceSelectionItemSelectedPreview() {
    ServiceSelectionItem(
        item = ServiceItem(
            id = 1, name = "基础护理", duration = 60, isSelected = true
        ), onClick = {})
}

@Preview
@Composable
fun ServiceSelectionItemUnselectedPreview() {
    ServiceSelectionItem(
        item = ServiceItem(
            id = 2, name = "康复训练", duration = 45, isSelected = false
        ), onClick = {})
}

@Preview
@Composable
fun NextStepButtonEnabledPreview() {
    NextStepButton(text = "开始服务", enabled = true, onClick = {})
}

@Preview
@Composable
fun NextStepButtonDisabledPreview() {
    NextStepButton(text = "开始服务", enabled = false, onClick = {})
}

@Composable
fun SelectAllButton(
    isAllSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary, containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFF2C85FE))
    ) {
        if (isAllSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "All selected",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "全选",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C85FE)
        )
    }
}

@Preview
@Composable
fun SelectAllButtonPreview() {
    SelectAllButton(isAllSelected = true, onClick = {})
}