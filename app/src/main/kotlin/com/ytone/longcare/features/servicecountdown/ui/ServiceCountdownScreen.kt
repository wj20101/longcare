package com.ytone.longcare.features.servicecountdown.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.utils.singleClick

/**
 * 服务倒计时页面
 * 
 * 功能：
 * 1. 显示服务倒计时和超时计时
 * 2. 管理前台服务和系统闹钟
 * 3. 处理照片上传和定位追踪
 * 4. 支持提前结束和正常结束服务
 * 
 * 优化点：
 * - 使用统一的时间计算逻辑，确保UI、通知、闹钟时间一致
 * - 生命周期恢复时仅刷新显示，不重新初始化
 * - 完善的资源清理机制
 * 
 * @param actions 页面导航与回传行为
 * @param orderKey 订单信息请求模型
 * @param projectIdList 选中的项目ID列表
 * @param sharedViewModel 共享的订单详情ViewModel
 * @param countdownViewModel 倒计时ViewModel
 * @param locationTrackingViewModel 定位追踪ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCountdownScreen(
    actions: ServiceCountdownActions,
    orderKey: OrderKey,
    projectIdList: List<Int>,
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel(),
    countdownViewModel: ServiceCountdownViewModel = hiltViewModel(),
    locationTrackingViewModel: LocationTrackingViewModel = hiltViewModel()
) {
    // 强制设置为竖屏
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    // 统一处理系统返回键，确保与导航按钮行为一致
    CustomBackHandler(customAction = actions.onNavigateHomeAndClearStack)

    // 从ViewModel获取状态
    val countdownState by countdownViewModel.countdownState.collectAsStateWithLifecycle()
    val formattedTime by countdownViewModel.formattedTime.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 订单状态异常弹窗状态
    var showOrderStateErrorDialog by remember { mutableStateOf(false) }
    var orderStateErrorMessage by remember { mutableStateOf("") }

    // 二次确认弹窗状态
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 权限相关状态
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDialogMessage by remember { mutableStateOf("") }
    

    // 通知权限请求启动器
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            permissionDialogMessage =
                "通知权限被拒绝，可能无法收到倒计时完成提醒。请到设置中手动开启通知权限。"
            showPermissionDialog = true
        }
    }

    // 精确闹钟权限请求启动器
    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 检查权限是否已授予
        if (!countdownViewModel.canScheduleExactAlarms()) {
            permissionDialogMessage =
                "精确闹钟权限被拒绝，可能无法准时收到倒计时完成提醒。请到设置中手动开启精确闹钟权限。"
            showPermissionDialog = true
        }
    }

    // 权限请求启动器
    val permissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderKey) }
    )

    ServiceCountdownLifecycleEffects(
        actions = actions,
        orderKey = orderKey,
        projectIdList = projectIdList,
        sharedViewModel = sharedViewModel,
        countdownViewModel = countdownViewModel,
        locationTrackingViewModel = locationTrackingViewModel,
        context = context,
        lifecycleOwner = lifecycleOwner,
        countdownState = countdownState,
        notificationPermissionLauncher = notificationPermissionLauncher,
        exactAlarmPermissionLauncher = exactAlarmPermissionLauncher,
        permissionLauncher = permissionLauncher,
        onPermissionDialogRequired = { message ->
            permissionDialogMessage = message
            showPermissionDialog = true
        },
        onOrderStateError = { message ->
            orderStateErrorMessage = message
            showOrderStateErrorDialog = true
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("服务时间倒计时", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = singleClick(onClick = actions.onNavigateHomeAndClearStack)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }, containerColor = Color.Transparent, modifier = Modifier.background(bgGradientBrush)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 可滚动的内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 100.dp), // 为底部按钮留出空间
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
                    onOpenPhotoUpload = {
                        val existingImages = countdownViewModel.getCurrentUploadedImages()
                        actions.onNavigateToPhotoUpload(orderKey, existingImages)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                SelectedServicesCard(
                    orderKey = orderKey,
                    projectIdList = projectIdList,
                    sharedViewModel = sharedViewModel
                )

            }

            ServiceCountdownBottomActionBar(
                countdownState = countdownState,
                onActionClick = {
                    if (!BuildConfig.USE_MOCK_DATA && !countdownViewModel.validatePhotosUploaded()) {
                        countdownViewModel.showToast("请上传照片")
                        return@ServiceCountdownBottomActionBar
                    }

                    if (countdownState == ServiceCountdownState.RUNNING) {
                        showConfirmDialog = true
                    } else {
                        handleEndService(
                            context = context,
                            orderKey = orderKey,
                            projectIdList = projectIdList,
                            countdownViewModel = countdownViewModel,
                            locationTrackingViewModel = locationTrackingViewModel,
                            actions = actions,
                            endType = 1
                        )
                    }
                }
            )
        }
    }

    PermissionAlertDialog(
        visible = showPermissionDialog,
        message = permissionDialogMessage,
        onDismiss = { showPermissionDialog = false },
        onNavigateSettings = {
            context.startActivity(
                buildPermissionSettingsIntent(context, permissionDialogMessage)
            )
        }
    )

    ConfirmEarlyEndServiceDialog(
        visible = showConfirmDialog,
        onDismiss = { showConfirmDialog = false },
        onConfirm = {
            handleEndService(
                context = context,
                orderKey = orderKey,
                projectIdList = projectIdList,
                countdownViewModel = countdownViewModel,
                locationTrackingViewModel = locationTrackingViewModel,
                actions = actions,
                endType = 2
            )
        }
    )

    OrderStateErrorDialog(
        visible = showOrderStateErrorDialog,
        message = orderStateErrorMessage,
        onConfirm = {
            showOrderStateErrorDialog = false
            handleOrderStateErrorAndExit(
                context = context,
                orderKey = orderKey,
                countdownViewModel = countdownViewModel,
                locationTrackingViewModel = locationTrackingViewModel,
                actions = actions
            )
        }
    )
}
