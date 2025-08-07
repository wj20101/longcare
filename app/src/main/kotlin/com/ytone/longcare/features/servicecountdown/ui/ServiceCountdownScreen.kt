package com.ytone.longcare.features.servicecountdown.ui

import android.content.pm.ActivityInfo
import android.os.SystemClock
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.navigateToNfcSignInForEndOrder
import com.ytone.longcare.navigation.navigateToPhotoUpload
import com.ytone.longcare.navigation.navigateToHomeAndClearStack
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import com.ytone.longcare.features.photoupload.model.ImageTaskType


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
    projectIdList: List<Int>,
    viewModel: ServiceCountdownViewModel = hiltViewModel(),
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel(),
    locationTrackingViewModel: LocationTrackingViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    // 从ViewModel获取状态
    val countdownState by viewModel.countdownState.collectAsStateWithLifecycle()
    val formattedTime by viewModel.formattedTime.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // 权限请求启动器
    val permissionLauncher = rememberLocationPermissionLauncher(
        locationTrackingViewModel = locationTrackingViewModel, orderId = orderId
    )

    // 检查定位权限和服务的函数
    fun checkLocationPermissionAndStart() {
        UnifiedPermissionHelper.checkLocationPermissionAndStart(context, permissionLauncher)
    }

    LaunchedEffect(orderId) {
        // 确保订单详情已加载到SharedViewModel中
        if (sharedViewModel.getCachedOrderInfo(orderId) == null) {
            sharedViewModel.getOrderInfo(orderId)
        }

        // 检查并启动定位服务
        checkLocationPermissionAndStart()

        // 监听图片上传结果
        navController.currentBackStackEntry?.savedStateHandle?.getStateFlow<Map<ImageTaskType, List<String>>?>(
            NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, null
        )?.collect { result ->
            result?.let {
                // 调用ViewModel处理图片上传结果
                viewModel.handlePhotoUploadResult(it)

                // 清除结果，避免重复处理
                navController.currentBackStackEntry?.savedStateHandle?.remove<Map<ImageTaskType, List<String>>>(
                    NavigationConstants.PHOTO_UPLOAD_RESULT_KEY
                )
            }
        }
    }

    // 设置倒计时时间
    LaunchedEffect(orderId, projectIdList) {
        val orderInfo = sharedViewModel.getCachedOrderInfo(orderId)
        orderInfo?.let {
            viewModel.setCountdownTimeFromProjects(
                orderId = orderId,
                projectList = it.projectList,
                selectedProjectIds = projectIdList
            )
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("服务时间倒计时", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateToHomeAndClearStack() }) {
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
        }, containerColor = Color.Transparent, modifier = Modifier.background(bgGradientBrush)
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
                formattedTime = formattedTime,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(24.dp))

            SelectedServicesCard(
                orderId = orderId, projectIdList = projectIdList, sharedViewModel = sharedViewModel
            )

            Spacer(modifier = Modifier.weight(1f))

            // End Service Button
            Button(
                onClick = {
                    // 验证照片是否已上传
                    if (!viewModel.validatePhotosUploaded()) {
                        viewModel.showToast("请上传照片")
                        return@Button
                    }

                    viewModel.endService(orderId)
                    val uploadedImages = viewModel.getCurrentUploadedImages()
                    val beginImgList = uploadedImages[ImageTaskType.BEFORE_CARE] ?: emptyList()
                    val endImgList = uploadedImages[ImageTaskType.AFTER_CARE] ?: emptyList()

                    navController.navigateToNfcSignInForEndOrder(
                        orderId = orderId,
                        params = EndOderInfo(
                            projectIdList = projectIdList,
                            beginImgList = beginImgList,
                            endImgList = endImgList
                        ),
                    )
                },
                enabled = countdownState == ServiceCountdownState.COMPLETED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (countdownState == ServiceCountdownState.COMPLETED) {
                        Color(0xFF4A90E2) // 蓝色
                    } else {
                        Color.Gray // 灰色（禁用状态）
                    }
                )
            ) {
                Text(
                    text = if (countdownState == ServiceCountdownState.COMPLETED) {
                        "结束服务"
                    } else {
                        "服务进行中..."
                    }, fontSize = 18.sp, color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CountdownTimerCard(
    navController: NavController,
    orderId: Long,
    countdownState: ServiceCountdownState,
    formattedTime: String = "12:00:00",
    viewModel: ServiceCountdownViewModel
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
                // 倒计时显示
                val (timeText, statusText) = when (countdownState) {
                    ServiceCountdownState.RUNNING -> formattedTime to "服务倒计时"
                    ServiceCountdownState.COMPLETED -> "00:00:00" to "服务倒计时"
                    ServiceCountdownState.ENDED -> "00:00:00" to "服务已结束"
                }

                Text(
                    text = timeText,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = statusText,
                    fontSize = 20.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Button(
                onClick = {
                    val existingImages = viewModel.getCurrentUploadedImages()
                    // 通过savedStateHandle传递已有的图片数据
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        NavigationConstants.EXISTING_IMAGES_KEY, existingImages
                    )
                    navController.navigateToPhotoUpload(orderId = orderId)
                }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF5A623) // 橙色
                )
            ) {
                Text("护理相册", color = Color.White)
            }
        }
    }
}

@Composable
fun SelectedServicesCard(
    orderId: Long, projectIdList: List<Int>, sharedViewModel: SharedOrderDetailViewModel
) {
    val tagHeightEstimate = 32.dp
    val tagOverlap = 12.dp

    // 获取订单详情
    val orderInfo = sharedViewModel.getCachedOrderInfo(orderId)

    // 过滤出选中的项目
    val selectedProjects =
        orderInfo?.projectList?.filter { it.projectId in projectIdList } ?: emptyList()

    Box {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tagHeightEstimate - tagOverlap),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp
                )
            ) {
                if (selectedProjects.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedProjects.forEachIndexed { index, project ->
                            Text("${index + 1}: ${project.projectName} (${project.serviceTime}分钟)")
                        }
                    }
                } else {
                    Text(
                        text = "暂无选中的服务项目", color = Color.Gray
                    )
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
    SelectedServicesCard(
        orderId = 12345L, projectIdList = listOf(1, 2), sharedViewModel = hiltViewModel()
    )
}