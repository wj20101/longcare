package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.DisplayDate
import com.ytone.longcare.common.utils.TimeUtils
import com.ytone.longcare.features.nursing.api.NursingActions
import com.ytone.longcare.features.nursing.vm.NursingViewModel
import com.ytone.longcare.model.handleOrderNavigation
import kotlinx.coroutines.launch

/**
 * 用于 UI 层的状态包装类，仅增加了一个 isSelected 字段来管理UI选择状态。
 */
internal data class UiDate(
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
    actions: NursingActions,
    viewModel: NursingViewModel = hiltViewModel()
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
            NursingTopBar()
        }, containerColor = Color.Transparent
    ) { paddingValues ->
        Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
            NursingDateTabsRow(
                dateList = dateList,
                pagerState = pagerState,
                selectedTabContentColor = selectedTabContentColor,
                unselectedTabContentColor = unselectedTabContentColor,
                onTabClick = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                PlanList(plans = orderList, isLoading = isLoading) { order ->
                    handleOrderNavigation(
                        state = order.state,
                        orderId = order.orderId,
                        planId = order.planId,
                        onNavigateToNursingExecution = actions.onNavigateToNursingExecution,
                        onNavigateToService = actions.onNavigateToService,
                        onNotStartedState = {
                            // 未开单状态，不允许跳转
                        }
                    )
                }
            }
        }
    }
}
