package com.ytone.longcare.features.nursingexecution.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.shared.vm.OrderDetailViewModel
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.navigation.navigateToNfcSignIn
import com.ytone.longcare.navigation.navigateToSelectService
import com.ytone.longcare.model.isExecutingState
import com.ytone.longcare.model.isPendingExecutionState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingExecutionScreen(
    navController: NavController,
    orderId: Long,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // 在组件初始化时加载订单信息
    LaunchedEffect(orderId) {
        viewModel.getOrderInfo(orderId)
    }
    when (val state = uiState) {
        is OrderDetailUiState.Loading -> {
            LoadingScreen()
        }
        
        is OrderDetailUiState.Success -> {
            NursingExecutionContent(
                navController = navController,
                orderInfo = state.orderInfo,
                orderId = orderId
            )
        }
        
        is OrderDetailUiState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = { viewModel.getOrderInfo(orderId) }
            )
        }
        
        is OrderDetailUiState.Initial -> {
            // 初始状态，显示加载
            LoadingScreen()
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text("重试")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingExecutionContent(
    navController: NavController,
    orderInfo: ServiceOrderInfoModel,
    orderId: Long
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.nursing_execution_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.nursing_execution_instruction),
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box {
                    ClientInfoCard(
                        modifier = Modifier.padding(top = 8.dp), 
                        orderInfo = orderInfo
                    )

                    ServiceHoursTag(tagText = stringResource(R.string.nursing_execution_client_info_tag))
                }

                Spacer(modifier = Modifier.weight(1f))

                ConfirmButton(
                    text = stringResource(R.string.nursing_execution_confirm_button), 
                    onClick = {
                        when {
                            orderInfo.state.isExecutingState() -> navController.navigateToSelectService(orderId)
                            orderInfo.state.isPendingExecutionState() -> navController.navigateToNfcSignIn(orderId)
                            else -> navController.popBackStack()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ClientInfoCard(modifier: Modifier, orderInfo: ServiceOrderInfoModel) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp) // 行之间的间距
        ) {
            InfoRow(label = stringResource(R.string.nursing_execution_label_name), value = orderInfo.userInfo.name)
            InfoRow(label = stringResource(R.string.nursing_execution_label_age), value = orderInfo.userInfo.age.toString())
            InfoRow(label = stringResource(R.string.nursing_execution_label_id_number), value = orderInfo.userInfo.identityCardNumber)
            InfoRow(label = stringResource(R.string.nursing_execution_label_address), value = orderInfo.userInfo.address)
            InfoRow(label = stringResource(R.string.nursing_execution_label_service_content), value = orderInfo.projectList.joinToString {it.projectName})
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top // 顶部对齐，以防地址过多行
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp) // 给标签一个固定宽度
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f) // 值占据剩余空间
        )
    }
}

@Composable
fun ConfirmButton(text: String, onClick: () -> Unit) {
    val gradientBrush = Brush.horizontalGradient(colors = listOf(Color(0xFF2B83FF), Color(0xFF3192FD)))

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = gradientBrush, shape = RoundedCornerShape(50)),
        shape = RoundedCornerShape(50), // 按钮本身的形状，用于点击涟漪效果等
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // 非常重要！
            contentColor = Color.White // 文字颜色设置为白色，以在渐变背景上可见
        ),
        contentPadding = PaddingValues() // 移除默认的内边距，因为我们将自己处理内容布局
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White // 确保文字颜色与渐变背景对比明显
        )
    }
}

// --- 预览 ---
@Preview(showBackground = true)
@Composable
fun NursingExecutionScreenPreview() {
    MaterialTheme { // 建议包裹在您的应用主题中
        NursingExecutionScreen(
            navController = rememberNavController(),
            orderId = 1L
        )
    }
}