package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.navigation.navigateToPhotoUpload
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory


// 服务倒计时页面状态
enum class ServiceCountdownState {
    RUNNING,    // 倒计时运行中
    COMPLETED,  // 倒计时完成
    ENDED       // 服务已结束
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCountdownScreen(
    navController: NavController,
    orderId: Long,
    viewModel: ServiceCountdownViewModel = hiltViewModel()
) {
    // 从ViewModel获取状态
    val countdownState by viewModel.countdownState.collectAsStateWithLifecycle()
    val formattedTime by viewModel.formattedTime.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("服务时间倒计时", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
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
        containerColor = Color.Transparent,
        modifier = Modifier.background(bgGradientBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请在服务倒计时结束后10分钟内结束服务",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Countdown Timer Card
            CountdownTimerCard(
                navController = navController,
                orderId = orderId,
                countdownState = countdownState,
                formattedTime = formattedTime
            )

            Spacer(modifier = Modifier.height(24.dp))

            SelectedServicesCard()

            Spacer(modifier = Modifier.weight(1f))

            // End Service Button
            Button(
                onClick = { 
                    viewModel.endService()
                    navController.navigateToPhotoUpload(
                        orderId = orderId,
                        address = "", // 可以从ViewModel或其他地方获取地址信息
                        projectIds = emptyList() // 可以从ViewModel或其他地方获取项目ID列表
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A90E2) // 蓝色
                )
            ) {
                Text("结束服务", fontSize = 18.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview
@Composable
fun ServiceCountdownScreenPreview() {
    val navController = rememberNavController()
    // 这里我们使用一个模拟的订单ID用于预览
    ServiceCountdownScreen(navController = navController, orderId = 12345L)
}

@Composable
fun CountdownTimerCard(
    navController: NavController,
    orderId: Long,
    countdownState: ServiceCountdownState,
    formattedTime: String = "12:00:00",
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 根据状态显示不同的倒计时文本
                when (countdownState) {
                    ServiceCountdownState.RUNNING -> {
                        Text(
                            text = formattedTime,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "服务倒计时",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    ServiceCountdownState.COMPLETED -> {
                        Text(
                            text = "00:00:00",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "服务倒计时",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    ServiceCountdownState.ENDED -> {
                        Text(
                            text = "00:00:00",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "服务已结束",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Button(
                onClick = { 
                    navController.navigateToPhotoUpload(
                        orderId = orderId,
                        address = "", // 地址信息可以从ViewModel获取
                        projectIds = emptyList() // 项目ID可以从ViewModel获取
                    )
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF5A623) // 橙色
                )
            ) {
                Text("护理相册", color = Color.White)
            }
        }
    }
}

@Preview
@Composable
fun CountdownTimerCardPreview() {
    val navController = rememberNavController()
    Column(modifier = Modifier.padding(16.dp)) {
        CountdownTimerCard(
            navController = navController,
            orderId = 12345L,
            countdownState = ServiceCountdownState.RUNNING,
            formattedTime = "02:35:16"
        )
        Spacer(modifier = Modifier.height(16.dp))
        CountdownTimerCard(
            navController = navController,
            orderId = 12345L,
            countdownState = ServiceCountdownState.COMPLETED
        )
        Spacer(modifier = Modifier.height(16.dp))
        CountdownTimerCard(
            navController = navController,
            orderId = 12345L,
            countdownState = ServiceCountdownState.ENDED
        )
    }
}

@Composable
fun SelectedServicesCard() {
    val tagHeightEstimate = 32.dp
    val tagOverlap = 12.dp
    Box {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = tagHeightEstimate - tagOverlap),
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1: 助浴服务")
                    Text("2: 做饭服务")
                    Text("3: 维修服务")
                    Text("4: 医护检查服务")
                }
            }
        }
        ServiceHoursTag(
            modifier = Modifier.align(Alignment.TopStart),
            tagText = "所选服务",
            tagCategory = TagCategory.DEFAULT
        )
    }
}

@Preview
@Composable
fun SelectedServicesCardPreview() {
    SelectedServicesCard()
}