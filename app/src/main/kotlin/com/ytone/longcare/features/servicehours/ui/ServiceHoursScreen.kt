package com.ytone.longcare.features.servicehours.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.ui.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag

// --- 数据模型 ---
data class ServiceRecord(
    val serviceType: String,
    val hours: Int,
    val dateTime: String
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceHoursScreen(navController: NavController) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "孙天成", // 示例名字，实际应动态传入
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "地址: 杭州市西湖区328弄24号", // 示例地址
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: 返回操作 */ }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
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
            // 使用 Box 实现 ServiceHoursTag 叠加在 ServiceRecordList 之上
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // 应用来自Scaffold的padding
            ) {
                // 列表内容区域，需要给顶部留出空间给 ServiceHoursTag
                ServiceRecordList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 18.dp) // 为 ServiceHoursTag 预留空间，可调整
                )

                // “已服务工时”标签，通过 offset 和对齐方式进行叠加
                ServiceHoursTag(modifier = Modifier.padding(start = 16.dp), tagText = "已服务工时")
            }
        }
    }
}

@Composable
fun ServiceRecordList(modifier: Modifier = Modifier) {
    val records = listOf(
        ServiceRecord("助浴服务", 8, "2025-4-30 12:30"),
        ServiceRecord("清洁服务", 8, "2025-4-30 12:30"),
        ServiceRecord("维修服务", 8, "2025-4-30 12:30"),
        ServiceRecord("理发服务", 8, "2025-4-30 12:30"),
        ServiceRecord("推拿服务", 8, "2025-4-30 12:30"),
        ServiceRecord("清洁服务", 8, "2025-4-30 12:30"),
        ServiceRecord("助浴服务", 8, "2025-4-30 12:30")
    )

    // 白色背景和顶部圆角的容器
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp) // 列表容器左右外边距
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(records) { index, record ->
                ServiceRecordItem(record)
                if (index < records.lastIndex) { // 不是最后一项才添加分割线
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
fun ServiceRecordItem(record: ServiceRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = record.serviceType,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "工时: ${record.hours}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "服务时间: ${record.dateTime}",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

// --- 预览 ---
@Preview(showBackground = true)
@Composable
fun ServiceHoursScreenPreview() {
    MaterialTheme {
        ServiceHoursScreen(navController = rememberNavController())
    }
}