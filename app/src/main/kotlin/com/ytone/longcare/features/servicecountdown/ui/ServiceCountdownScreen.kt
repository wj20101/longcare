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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import kotlinx.coroutines.flow.MutableStateFlow

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
    viewModel: Any = hiltViewModel()
) {
    // 从ViewModel获取状态
    val countdownState by when (viewModel) {
        is ServiceCountdownViewModel -> viewModel.countdownState.collectAsStateWithLifecycle()
        is PreviewServiceCountdownViewModel -> viewModel.countdownState.collectAsState()
        else -> remember { mutableStateOf(ServiceCountdownState.RUNNING) }
    }
    val formattedTime by when (viewModel) {
        is ServiceCountdownViewModel -> viewModel.formattedTime.collectAsStateWithLifecycle()
        is PreviewServiceCountdownViewModel -> viewModel.formattedTime.collectAsState()
        else -> remember { mutableStateOf("12:00:00") }
    }
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
                countdownState = countdownState,
                formattedTime = formattedTime,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(24.dp))

            SelectedServicesCard()

            Spacer(modifier = Modifier.weight(1f))

            // End Service Button
            Button(
                onClick = { 
                    when (viewModel) {
                        is ServiceCountdownViewModel -> viewModel.endService()
                        is PreviewServiceCountdownViewModel -> viewModel.setCountdownState(ServiceCountdownState.ENDED)
                    }
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

@Composable
fun CountdownTimerCard(
    countdownState: ServiceCountdownState,
    formattedTime: String = "12:00:00",
    viewModel: Any? = null
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
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "服务倒计时",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                    ServiceCountdownState.COMPLETED -> {
                        Text(
                            text = "00:00:00",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "服务倒计时",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                    ServiceCountdownState.ENDED -> {
                        Text(
                            text = "00:00:00",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "服务已结束",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            Button(
                onClick = { /*TODO*/ },
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

// 预览用的简化ViewModel
class PreviewServiceCountdownViewModel : ViewModel() {
    val countdownState = MutableStateFlow(ServiceCountdownState.RUNNING)
    val formattedTime = MutableStateFlow("12:00:00")
    
    fun endService() {
        countdownState.value = ServiceCountdownState.ENDED
    }
    
    fun setCountdownState(state: ServiceCountdownState) {
        countdownState.value = state
    }
    
    fun setFormattedTime(time: String) {
        formattedTime.value = time
    }
}

@Preview(showBackground = true)
@Composable
fun ServiceCountdownScreenRunningPreview() {
    val previewViewModel = remember { 
        PreviewServiceCountdownViewModel().apply {
            setCountdownState(ServiceCountdownState.RUNNING)
        }
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    ) {
        ServiceCountdownScreen(
            navController = rememberNavController(), 
            orderId = 1L,
            viewModel = previewViewModel
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ServiceCountdownScreenCompletedPreview() {
    val previewViewModel = remember { 
        PreviewServiceCountdownViewModel().apply {
            setCountdownState(ServiceCountdownState.COMPLETED)
            setFormattedTime("00:00:00")
        }
    }
    
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    ) {
        ServiceCountdownScreen(
            navController = rememberNavController(), 
            orderId = 1L,
            viewModel = previewViewModel
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ServiceCountdownScreenEndedPreview() {
    val previewViewModel = remember { 
        PreviewServiceCountdownViewModel().apply {
            setCountdownState(ServiceCountdownState.ENDED)
            setFormattedTime("00:00:00")
        }
    }
    
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    ) {
        ServiceCountdownScreen(
            navController = rememberNavController(), 
            orderId = 1L,
            viewModel = previewViewModel
        )
    }
}