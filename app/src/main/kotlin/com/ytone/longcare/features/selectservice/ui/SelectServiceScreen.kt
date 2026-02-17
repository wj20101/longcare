package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.shared.vm.StarOrderUiState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.features.selectservice.api.SelectServiceActions
import com.ytone.longcare.features.selectservice.vm.SelectServiceViewModel
import com.ytone.longcare.model.OrderKey

// --- 数据模型 ---
data class ServiceItem(
    val id: Int, val name: String, val duration: Int, // 分钟
    var isSelected: Boolean = false
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectServiceScreen(
    actions: SelectServiceActions,
    orderKey: OrderKey,
    selectServiceViewModel: SelectServiceViewModel = hiltViewModel(),
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 使用SharedViewModel获取订单详情
    val uiState by sharedViewModel.uiState.collectAsStateWithLifecycle()
    val starOrderState by sharedViewModel.starOrderState.collectAsStateWithLifecycle()

    // 统一处理系统返回键
    CustomBackHandler(customAction = actions.onNavigateBack)

    // 在组件初始化时加载订单信息（如果缓存中没有）
    LaunchedEffect(orderKey) {
        // 先检查缓存，如果没有缓存数据才请求
        if (sharedViewModel.getCachedOrderInfo(orderKey) == null) {
            sharedViewModel.getOrderInfo(orderKey)
        } else {
            // 如果有缓存数据，直接设置为成功状态
            sharedViewModel.getOrderInfo(orderKey, forceRefresh = false)
        }
    }

    // 监听starOrder状态，成功后执行路由跳转
    LaunchedEffect(starOrderState) {
        if (starOrderState is StarOrderUiState.Success) {
            // 重置状态
            sharedViewModel.resetStarOrderState()
        }
    }

    var selectServiceType by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        selectServiceType = selectServiceViewModel.getSelectServiceType()
    }

    // 根据API返回的数据转换为UI需要的ServiceItem格式
    val serviceItems = remember { mutableStateListOf<ServiceItem>() }

    // 当uiState变化时更新serviceItems
    LaunchedEffect(uiState, selectServiceType) {
        when (val currentState = uiState) {
            is OrderDetailUiState.Success -> {
                serviceItems.clear()
                serviceItems.addAll(
                    (currentState.orderInfo.projectList ?: emptyList()).map { project ->
                        ServiceItem(
                            id = project.projectId,
                            name = project.projectName,
                            duration = project.serviceTime,
                            isSelected = selectServiceType != 0 // 如果不等于0，则默认全选
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
                    IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 可滚动的内容区域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 120.dp), // 为底部按钮留出空间
                    horizontalAlignment = Alignment.CenterHorizontally
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

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 固定在底部的按钮区域
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFF6F9FF).copy(alpha = 0.9f),
                                    Color(0xFFF6F9FF)
                                ), startY = 0f, endY = 100f
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 32.dp)
                ) {
                    // 底部按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectServiceType == 0) {
                            // 全选按钮
                            SelectAllButton(
                                isAllSelected = serviceItems.isNotEmpty() && serviceItems.all { it.isSelected },
                                enabled = starOrderState !is StarOrderUiState.Loading,
                                onClick = {
                                    val isAllSelected = serviceItems.all { it.isSelected }
                                    for (i in serviceItems.indices) {
                                        serviceItems[i] =
                                            serviceItems[i].copy(isSelected = !isAllSelected)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // 下一步按钮
                        NextStepButton(
                            text = if (starOrderState !is StarOrderUiState.Loading) "开始服务" else "正在处理...",
                            enabled = serviceItems.any { it.isSelected } && starOrderState !is StarOrderUiState.Loading,
                            onClick = singleClick {
                                val selectedProjectIds =
                                    serviceItems.filter { it.isSelected }.map { it.id }
                                // 先调用starOrder接口
                                sharedViewModel.starOrder(
                                    orderKey.orderId,
                                    selectedProjectIds.map { it.toLong() }) {
                                    // 成功后保存选中的项目ID到Room
                                    coroutineScope.launch {
                                        selectServiceViewModel.updateSelectedProjects(
                                            orderKey = orderKey,
                                            selectedProjectIds = selectedProjectIds
                                        )
                                        actions.onNavigateToServiceCountdown(
                                            orderKey,
                                            selectedProjectIds
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
}
