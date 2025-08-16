package com.ytone.longcare.features.servicecomplete.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.navigation.navigateToHomeAndClearStack
import com.ytone.longcare.R
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import com.ytone.longcare.common.utils.HomeBackHandler

// --- 数据模型 ---
data class ServiceSummary(
    val clientName: String,
    val clientAge: Int,
    val clientIdNumber: String,
    val clientAddress: String,
    val serviceContent: String,
    val duration: String
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCompleteScreen(
    navController: NavController, orderId: Long, sharedViewModel: SharedOrderDetailViewModel = hiltViewModel()
) {
    val uiState by sharedViewModel.uiState.collectAsStateWithLifecycle()
    
    // 统一处理系统返回键，与导航按钮行为一致（返回首页并清空堆栈）
    HomeBackHandler(navController = navController)

    // 在组件初始化时获取订单详情（如果缓存中没有）
    LaunchedEffect(orderId) {
        // 先检查缓存，如果没有缓存数据才请求
        if (sharedViewModel.getCachedOrderInfo(orderId) == null) {
            sharedViewModel.getOrderInfo(orderId)
        } else {
            // 如果有缓存数据，直接设置为成功状态
            sharedViewModel.getOrderInfo(orderId, forceRefresh = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(topBar = {
            CenterAlignedTopAppBar(
                title = { Text("服务完成", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateToHomeAndClearStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }, containerColor = Color.Transparent, bottomBar = {
            // 将按钮放在 bottomBar 中使其固定在底部
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                ActionButton(text = "完成", onClick = {
                    navController.navigateToHomeAndClearStack()
                })
            }
        }) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), // 应用来自Scaffold的padding (包括了底部按钮的空间)
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                item {
                    Text(
                        text = "已完成服务，请确认服务内容", color = Color.White, fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    ThankYouCard()
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    when (val state = uiState) {
                        is OrderDetailUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }

                        is OrderDetailUiState.Success -> {
                            val orderInfo = state.orderInfo
                            val serviceSummary = ServiceSummary(
                                clientName = orderInfo.userInfo.name,
                                clientAge = orderInfo.userInfo.age,
                                clientIdNumber = orderInfo.userInfo.identityCardNumber,
                                clientAddress = orderInfo.userInfo.address,
                                serviceContent = orderInfo.projectList.joinToString(", ") { it.projectName },
                                duration = formatServiceDuration(orderInfo.projectList.sumOf { it.serviceTime })
                            )
                            ServiceChecklistSection(summary = serviceSummary)
                        }

                        is OrderDetailUiState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "加载订单信息失败",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.message,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is OrderDetailUiState.Initial -> {
                            // 初始状态，显示加载指示器
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 格式化服务时长
 * @param totalMinutes 总分钟数
 * @return 格式化后的时长字符串，如"2小时30分钟"
 */
fun formatServiceDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
        hours > 0 -> "${hours}小时"
        minutes > 0 -> "${minutes}分钟"
        else -> "0分钟"
    }
}

@Preview
@Composable
fun ServiceCompleteScreenPreview() {
    // Preview中使用假数据，避免依赖真实的ViewModel
    val serviceSummary = ServiceSummary(
        clientName = "孙连中",
        clientAge = 72,
        clientIdNumber = "310023023020320302",
        clientAddress = "浙江省杭州市西湖区爱家小区32号501",
        serviceContent = "助浴",
        duration = "3小时21分钟"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            item {
                Text(
                    text = "已完成服务，请确认服务内容", color = Color.White, fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                ThankYouCard()
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                ServiceChecklistSection(summary = serviceSummary)
            }
        }
    }
}

// --- UI 子组件 ---

@Composable
fun ThankYouCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.service_complete_illustration),
                contentDescription = "服务完成",
                modifier = Modifier
                    .height(150.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "设备已断开",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "感谢您真挚的服务，辛苦了。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview
@Composable
fun ThankYouCardPreview() {
    ThankYouCard()
}


@Composable
fun ServiceChecklistSection(summary: ServiceSummary) {
    // 使用与“照片上传”界面类似的叠加布局
    val tagHeightEstimate = 32.dp
    val tagOverlap = 12.dp

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tagHeightEstimate - tagOverlap),
            shape = RoundedCornerShape(
                topStart = 0.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    top = tagOverlap + 16.dp, // 为标签留出空间
                    bottom = 16.dp, start = 16.dp, end = 16.dp
                ), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChecklistItem("姓名:", summary.clientName)
                ChecklistItem("年龄:", summary.clientAge.toString())
                ChecklistItem("身份证号:", summary.clientIdNumber)
                ChecklistItem("地址:", summary.clientAddress)
                ChecklistItem("服务内容:", summary.serviceContent)
                ChecklistItem("用时:", summary.duration)
            }
        }

        // 标题标签
        ServiceHoursTag(
            modifier = Modifier, tagText = "服务清单", tagCategory = TagCategory.DEFAULT
        )
    }
}

@Preview
@Composable
fun ServiceChecklistSectionPreview() {
    val summary = ServiceSummary(
        clientName = "John Doe",
        clientAge = 75,
        clientIdNumber = "123456789012345678",
        clientAddress = "123 Main St, Anytown, USA",
        serviceContent = "Bathing Assistance",
        duration = "2 hours 30 minutes"
    )
    ServiceChecklistSection(summary = summary)
}

@Composable
fun ChecklistItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview
@Composable
fun ChecklistItemPreview() {
    ChecklistItem(label = "Name:", value = "John Doe")
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF5CA0FF), Color(0xFF2A8CFF))
    )
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = buttonGradient, shape = CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview
@Composable
fun ActionButtonPreview() {
    ActionButton(text = "Submit", onClick = {})
}
