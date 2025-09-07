package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.common.utils.DisplayDate
import com.ytone.longcare.common.utils.TimeUtils
import com.ytone.longcare.features.nursing.vm.NursingViewModel
import com.ytone.longcare.model.handleOrderNavigation
import com.ytone.longcare.model.isPendingCareState
import com.ytone.longcare.model.toStateDisplayText
import com.ytone.longcare.navigation.navigateToNursingExecution
import com.ytone.longcare.navigation.navigateToService
import com.ytone.longcare.theme.LongCareTheme
import kotlinx.coroutines.launch

/**
 * 用于 UI 层的状态包装类，仅增加了一个 isSelected 字段来管理UI选择状态。
 */
private data class UiDate(
    val displayInfo: DisplayDate
)

// 缓存当月日期列表，避免重复计算
private val currentMonthDateList by lazy {
    TimeUtils.getCurrentMonthDateList().map { displayDate ->
        UiDate(displayInfo = displayDate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingScreen(
    navController: NavController, viewModel: NursingViewModel = hiltViewModel()
) {
    val dateList = remember { currentMonthDateList }
    val initialPage = remember { dateList.indexOfFirst { it.displayInfo.isToday }.coerceAtLeast(0) }
    var selectedTabIndex by remember { mutableIntStateOf(initialPage) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { dateList.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedTabContentColor = Color(0xFF4A86E8) // 选中 Tab 的文字颜色
    val unselectedTabContentColor = Color.White // 未选中 Tab 的文字颜色

    // 观察ViewModel状态
    val orderList by viewModel.orderListState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // 初始化时加载今天的数据
    LaunchedEffect(Unit) {
        val selectedDate = dateList[selectedTabIndex]
        val dateString = TimeUtils.formatDateForApi(selectedDate.displayInfo)
        viewModel.getOrderList(dateString)
    }

    // 当选中的日期改变时，获取对应日期的订单数据
    LaunchedEffect(selectedTabIndex) {
        val selectedDate = dateList[selectedTabIndex]
        // 将DisplayDate转换为API需要的格式
        val dateString = TimeUtils.formatDateForApi(selectedDate.displayInfo)
        viewModel.getOrderList(dateString)
    }

    // 同步 LazyRow 和 HorizontalPager 的滚动状态
    LaunchedEffect(selectedTabIndex) {
        pagerState.animateScrollToPage(selectedTabIndex)
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    Scaffold(
        modifier = Modifier, topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "护理工作",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                ),
            )
        }, containerColor = Color.Transparent
    ) { paddingValues ->
        Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
            val lazyListState = rememberLazyListState()

            val density = LocalDensity.current

            // 同步LazyRow和HorizontalPager的滚动状态，选中项居中显示
            LaunchedEffect(pagerState.currentPage) {
                val targetIndex = pagerState.currentPage
                if (targetIndex >= 0 && targetIndex < dateList.size) {
                    // 使用更简单的方式计算居中偏移
                    // 每个tab项约65dp宽度，让选中项居中显示
                    val itemWidthPx = with(density) { 65.dp.toPx() }
                    val centerOffsetPx = (itemWidthPx * 2).toInt() // 简化的居中偏移计算

                    lazyListState.animateScrollToItem(
                        index = targetIndex,
                        scrollOffset = -centerOffsetPx
                    )
                }
            }

            LazyRow(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(
                    items = dateList,
                    key = { index, _ -> index }
                ) { index, dateInfo ->
                    val isSelected = pagerState.currentPage == index

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .then(
                                if (isSelected) Modifier.background(Color.White) else Modifier
                            )
                            .clickable {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dateInfo.displayInfo.dayOfWeek,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) selectedTabContentColor else unselectedTabContentColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dateInfo.displayInfo.dateLabel,
                                fontSize = 12.sp,
                                color = if (isSelected) selectedTabContentColor else unselectedTabContentColor
                            )
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                PlanList(plans = orderList, isLoading = isLoading) { order ->
                    handleOrderNavigation(
                         state = order.state,
                         orderId = order.orderId,
                         planId = order.planId,
                         onNavigateToNursingExecution = { orderId, planId ->
                             navController.navigateToNursingExecution(OrderInfoRequestModel(orderId = orderId, planId = planId))
                         },
                         onNavigateToService = { orderId, planId ->
                             navController.navigateToService(OrderInfoRequestModel(orderId = orderId, planId = planId))
                         },
                         onNotStartedState = {
                             // 未开单状态，不允许跳转
                         }
                     )
                }
            }
        }
    }
}

/**
 * 计划列表，拥有一个整体的、顶部圆角的白色背景。
 */
@Composable
fun PlanList(plans: List<ServiceOrderModel>, isLoading: Boolean, onGoToDetailClick: (ServiceOrderModel) -> Unit) {
    val modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        .background(Color.White)
    if (isLoading) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (plans.isEmpty()) {
        // 空状态视图
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "暂无服务订单",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前日期没有安排服务订单",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    } else {
        Column(
            modifier = modifier,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(plans) { index, plan ->
                    OrderListItem(
                        modifier = Modifier.clickable { onGoToDetailClick.invoke(plan) },
                        item = plan
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = Color(0xFFF0F0F0)
                    )
                    if (index == plans.lastIndex) {
                        // 底部留出一些空间
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}


/**
 * 计划列表项，移除了 Card, 改为简单的 Row 布局
 */
@Composable
fun OrderListItem(modifier: Modifier = Modifier, item: ServiceOrderModel) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.service_order_work_hours, item.planTotalTime),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "地址: ${item.liveAddress}",
                color = Color.Gray,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        Text(
            text = item.state.toStateDisplayText(),
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.common_details),
            tint = Color.LightGray
        )
    }
}


// --- 预览 ---
@Preview
@Composable
fun PlanListPreview() {
    val plans = listOf(
        ServiceOrderModel(
            orderId = 1L,
            name = "John Doe",
            planTotalTime = 60,
            liveAddress = "123 Main St",
            state = 0
        ),
        ServiceOrderModel(
            orderId = 2L,
            name = "Jane Smith",
            planTotalTime = 90,
            liveAddress = "456 Oak Ave",
            state = 1
        )
    )
    PlanList(plans = plans, isLoading = false, onGoToDetailClick = {})
}

@Preview
@Composable
fun PlanListEmptyPreview() {
    PlanList(plans = emptyList(), isLoading = false, onGoToDetailClick = {})
}

@Preview
@Composable
fun PlanListWithSingleItemPreview() {
    val plans = listOf(
        ServiceOrderModel(
            orderId = 1L,
            name = "John Doe",
            planTotalTime = 60,
            liveAddress = "123 Main St",
            state = 0
        )
    )
    PlanList(plans = plans, isLoading = false, onGoToDetailClick = {})
}

@Preview(showBackground = true)
@Composable
fun PlanListPreviewEmpty() {
    LongCareTheme {
        PlanList(plans = emptyList(), isLoading = false, onGoToDetailClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PlanListPreviewWithData() {
    val samplePlans = listOf(
        ServiceOrderModel(orderId = 1, name = "张三", planTotalTime = 60, liveAddress = "北京市朝阳区 xxx 街道 123 号", state = 0),
        ServiceOrderModel(orderId = 2, name = "李四", planTotalTime = 90, liveAddress = "上海市浦东新区 yyy 路 456 号", state = 1),
        ServiceOrderModel(orderId = 3, name = "王五", planTotalTime = 45, liveAddress = "广州市天河区 zzz 大道 789 号", state = 2)
    )
    LongCareTheme {
        PlanList(plans = samplePlans, isLoading = false, onGoToDetailClick = {})
    }
}