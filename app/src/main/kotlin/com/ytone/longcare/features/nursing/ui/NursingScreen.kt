package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.features.nursing.viewmodel.NursingViewModel
import com.ytone.longcare.theme.LongCareTheme
import kotlinx.coroutines.launch

// --- 数据模型 (与之前相同) ---
data class DateInfo(val dayOfWeek: String, val date: String)
data class PlanItem(
    val name: String, val hours: Int, val service: String, val address: String, val status: String?
)

// --- 主屏幕 (重构后) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingScreen(
    navController: NavController, viewModel: NursingViewModel = hiltViewModel()
) {
    val dates = remember { createDateList() }
    val pagerState = rememberPagerState { dates.size }
    val coroutineScope = rememberCoroutineScope()

    val selectedTabContentColor = Color(0xFF4A86E8) // 选中 Tab 的文字颜色
    val unselectedTabContentColor = Color.White // 未选中 Tab 的文字颜色

    Scaffold(
        modifier = Modifier, topBar = {
            TopAppBar(
                title = {
                    Text(
                        "待护理计划",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: 返回操作 */ }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = { Spacer(modifier = Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        }, containerColor = Color.Transparent
    ) { paddingValues ->
        Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                divider = {},
                indicator = {}
            ) {
                dates.forEachIndexed { index, dateInfo ->
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .then(
                                if (isSelected) Modifier.background(Color.White) else Modifier
                            ),
                        selectedContentColor = selectedTabContentColor,
                        unselectedContentColor = unselectedTabContentColor,
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dateInfo.dayOfWeek,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateInfo.date,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val planList = remember(page) { createPlanList().shuffled() }
                PlanList(plans = planList)
            }
        }
    }
}

/**
 * 计划列表，现在只包含 LazyColumn
 */
@Composable
fun PlanList(plans: List<PlanItem>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(plans) { plan ->
            PlanListItem(item = plan) // PlanListItem 组件与之前版本相同，可复用
        }
    }
}


// --- 之前版本的 PlanListItem 组件 (无需修改) ---
@Composable
fun PlanListItem(item: PlanItem) {
    Card(
        onClick = { /* TODO: 跳转到详情 */ },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "工时: ${item.hours}", color = Color.Gray, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${item.service}  地址: ${item.address}",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            item.status?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "详情",
                tint = Color.LightGray
            )
        }
    }
}


// --- 模拟数据创建函数 ---
private fun createDateList(): List<DateInfo> {
    return listOf(
        DateInfo("昨天", "05/10"),
        DateInfo("今天", "05/11"),
        DateInfo("明天", "05/12"),
        DateInfo("周二", "05/13"),
        DateInfo("周三", "05/14"),
        DateInfo("周四", "05/15"),
        DateInfo("周五", "05/16"),
        DateInfo("周六", "05/17")
    )
}

private fun createPlanList(): List<PlanItem> {
    return listOf(
        PlanItem("孙天成", 8, "助浴服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("王东明", 8, "清洁服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("胡来德", 8, "维修服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("丛敏丽", 8, "理发服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("爱德福", 8, "推拿服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("丁成立", 8, "清洁服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("张爱国", 8, "清洁服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("王阳明", 8, "清洁服务", "杭州市西湖区328弄24号", "未完成"),
        PlanItem("陈福记", 8, "孙连仲", "杭州市西湖区328弄24号", null)
    )
}

// --- 预览 ---
@Preview(showBackground = true)
@Composable
fun NursingScreenPreview() {
    LongCareTheme {
        NursingScreen(navController = rememberNavController())
    }
}