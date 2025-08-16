package com.ytone.longcare.features.servicecountdown.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import com.ytone.longcare.common.utils.HomeBackHandler


// 服务倒计时页面状态
enum class ServiceCountdownState {
    RUNNING,    // 倒计时运行中
    COMPLETED,  // 倒计时完成
    OVERTIME,   // 倒计时超时
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
    
    // 统一处理系统返回键，确保与导航按钮行为一致
    HomeBackHandler(navController = navController)

    // 从ViewModel获取状态
    val countdownState by viewModel.countdownState.collectAsStateWithLifecycle()
    val formattedTime by viewModel.formattedTime.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 二次确认弹窗状态
    var showConfirmDialog by remember { mutableStateOf(false) }

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

    // 设置倒计时时间的通用函数
    val setupCountdownTime = {
        val orderInfo = sharedViewModel.getCachedOrderInfo(orderId)
        orderInfo?.let {
            viewModel.setCountdownTimeFromProjects(
                orderId = orderId,
                projectList = it.projectList,
                selectedProjectIds = projectIdList
            )
        }
    }

    // 初始设置倒计时时间
    LaunchedEffect(orderId, projectIdList) {
        setupCountdownTime()
    }

    // 监听生命周期变化，在页面恢复时重新计算倒计时
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            setupCountdownTime()
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

                    // 如果倒计时还在进行中，显示确认弹窗
                    if (countdownState == ServiceCountdownState.RUNNING) {
                        showConfirmDialog = true
                    } else {
                        // 直接结束服务
                        viewModel.endService(orderId)
                        val uploadedImages = viewModel.getCurrentUploadedImages()
                        val beginImgList = uploadedImages[ImageTaskType.BEFORE_CARE] ?: emptyList()
                        val endImgList = uploadedImages[ImageTaskType.AFTER_CARE] ?: emptyList()

                        navController.navigateToNfcSignInForEndOrder(
                            orderId = orderId,
                            params = EndOderInfo(
                                projectIdList = projectIdList,
                                beginImgList = beginImgList,
                                endImgList = endImgList,
                                endType = 1
                            ),
                        )
                    }
                },
                enabled = countdownState != ServiceCountdownState.ENDED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (countdownState) {
                        ServiceCountdownState.RUNNING -> Color(0xFFFF9500) // 橙色（提前结束）
                        ServiceCountdownState.COMPLETED, ServiceCountdownState.OVERTIME -> Color(0xFF4A90E2) // 蓝色（正常结束）
                        ServiceCountdownState.ENDED -> Color.Gray // 灰色（已结束）
                    }
                )
            ) {
                Text(
                    text = when (countdownState) {
                        ServiceCountdownState.RUNNING -> "提前结束服务"
                        ServiceCountdownState.COMPLETED, ServiceCountdownState.OVERTIME -> "结束服务"
                        ServiceCountdownState.ENDED -> "服务已结束"
                    }, fontSize = 18.sp, color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // 二次确认弹窗
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认提前结束服务") },
            text = { Text("服务时间尚未结束，确定要提前结束服务吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.endService(orderId)
                        val uploadedImages = viewModel.getCurrentUploadedImages()
                        val beginImgList = uploadedImages[ImageTaskType.BEFORE_CARE] ?: emptyList()
                        val endImgList = uploadedImages[ImageTaskType.AFTER_CARE] ?: emptyList()

                        navController.navigateToNfcSignInForEndOrder(
                             orderId = orderId,
                             params = EndOderInfo(
                                 projectIdList = projectIdList,
                                 beginImgList = beginImgList,
                                 endImgList = endImgList,
                                 endType = 2  // 提前结束
                             ),
                         )
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
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
                    ServiceCountdownState.OVERTIME -> formattedTime to "服务超时"
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
    val allProjects = orderInfo?.projectList ?: emptyList()

    // 判断是否为全选状态
    val isAllSelected = projectIdList.isEmpty() || 
        (allProjects.isNotEmpty() && projectIdList.containsAll(allProjects.map { it.projectId }))

    // 根据是否全选来确定显示的项目
    val selectedProjects = if (isAllSelected) {
        allProjects
    } else {
        allProjects.filter { it.projectId in projectIdList }
    }

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
                    // 如果是全选状态，显示全选提示
                    if (isAllSelected && allProjects.size > 1) {
                        Text(
                            text = "已选择全部服务项目 (${allProjects.size}项)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4A90E2)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
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
            tagText = if (isAllSelected && allProjects.size > 1) "全选服务" else "所选服务",
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