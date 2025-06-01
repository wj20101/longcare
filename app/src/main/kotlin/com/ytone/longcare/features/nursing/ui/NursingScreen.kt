package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
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

// --- 1. 数据模型 ---

data class DateInfo(val dayOfWeek: String, val date: String)
data class PlanItem(
    val name: String,
    val hours: Int,
    val service: String,
    val address: String,
    val status: String? // 状态可以为空，例如已完成的就不显示
)


// --- 2. UI 子组件 ---

/**
 * 横向滚动的日期选择器
 */
@Composable
fun DateSelector(
    dates: List<DateInfo>,
    selectedDateIndex: Int,
    onDateSelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(dates) { index, dateInfo ->
            DateChip(
                dateInfo = dateInfo,
                isSelected = index == selectedDateIndex,
                onClick = { onDateSelected(index) }
            )
        }
    }
}

/**
 * 单个日期“芯片”
 */
@Composable
fun DateChip(dateInfo: DateInfo, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dateInfo.dayOfWeek,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = dateInfo.date,
            color = contentColor,
            fontSize = 12.sp
        )
    }
}

/**
 * 计划列表项
 */
@Composable
fun PlanListItem(item: PlanItem) {
    Card(
        onClick = { /* TODO: 跳转到详情 */ },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "工时: ${item.hours}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${item.service}  地址: ${item.address}",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp // 增加行高以改善可读性
                )
            }

            // 右侧状态和箭头
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


// --- 3. 主屏幕 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingScreen(
    navController: NavController,
    viewModel: NursingViewModel = hiltViewModel()
) {
    // 模拟数据
    val dates = listOf(
        DateInfo("昨天", "05/10"),
        DateInfo("今天", "05/11"),
        DateInfo("明天", "05/12"),
        DateInfo("周二", "05/13"),
        DateInfo("周三", "05/14"),
        DateInfo("周四", "05/15"),
        DateInfo("周五", "05/16"),
    )
    val plans = listOf(
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

    var selectedDateIndex by remember { mutableIntStateOf(1) } // 默认选中“今天”

    Scaffold(
        topBar = {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 保留一个空的 action, 让标题能够完美居中
                    Spacer(modifier = Modifier.width(48.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Spacer(modifier = Modifier.height(16.dp))
            // 日期选择器
            DateSelector(
                dates = dates,
                selectedDateIndex = selectedDateIndex,
                onDateSelected = { selectedDateIndex = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 计划列表
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp) // 卡片之间的间距
            ) {
                items(plans) { plan ->
                    PlanListItem(item = plan)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NursingScreenPreview() {
    LongCareTheme {
        NursingScreen(navController = rememberNavController())
    }
}