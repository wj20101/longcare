package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.navigation.navigateToEndServiceSelection
import com.ytone.longcare.navigation.navigateToPhotoUpload
import com.ytone.longcare.navigation.navigateToHomeAndClearStack
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import androidx.core.net.toUri
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.api.response.ServiceOrderStateModel
import com.ytone.longcare.common.utils.HomeBackHandler
import com.ytone.longcare.di.ServiceCountdownEntryPoint
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import dagger.hilt.android.EntryPointAccessors
import com.ytone.longcare.navigation.OrderNavParams
import com.ytone.longcare.navigation.toRequestModel
import com.ytone.longcare.common.utils.singleClick


// 服务倒计时页面状态
enum class ServiceCountdownState {
    RUNNING,    // 倒计时运行中
    COMPLETED,  // 倒计时完成
    OVERTIME,   // 倒计时超时
    ENDED       // 服务已结束
}

/**
 * 倒计时初始化状态
 * 用于统一管理初始化相关的状态变量
 */
private data class CountdownInitState(
    val isInitialized: Boolean = false,
    val lastProjectIdList: List<Int> = emptyList(),
    val permissionsChecked: Boolean = false
)

/**
 * 服务信息
 * 用于缓存计算结果，避免重复计算
 */
private data class ServiceInfo(
    val serviceName: String,
    val totalMinutes: Int
)

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
 * @param navController 导航控制器
 * @param orderParams 订单信息请求模型
 * @param projectIdList 选中的项目ID列表
 * @param sharedViewModel 共享的订单详情ViewModel
 * @param countdownViewModel 倒计时ViewModel
 * @param locationTrackingViewModel 定位追踪ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCountdownScreen(
    navController: NavController,
    orderParams: OrderNavParams,
    projectIdList: List<Int>,
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel(),
    countdownViewModel: ServiceCountdownViewModel = hiltViewModel(),
    locationTrackingViewModel: LocationTrackingViewModel = hiltViewModel()
) {
    // 从订单导航参数构建请求模型
    val orderInfoRequest = remember(orderParams) { orderParams.toRequestModel() }
    
    // 强制设置为竖屏
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    // 统一处理系统返回键，确保与导航按钮行为一致
    HomeBackHandler(navController = navController)

    // 从ViewModel获取状态
    val countdownState by countdownViewModel.countdownState.collectAsStateWithLifecycle()
    val formattedTime by countdownViewModel.formattedTime.collectAsStateWithLifecycle()
    val orderStateError by countdownViewModel.orderStateError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 订单状态异常弹窗状态
    var showOrderStateErrorDialog by remember { mutableStateOf(false) }
    var orderStateErrorMessage by remember { mutableStateOf("") }

    // 获取CountdownNotificationManager实例
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext, ServiceCountdownEntryPoint::class.java
    )
    val countdownNotificationManager = entryPoint.countdownNotificationManager()

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
        if (!countdownNotificationManager.canScheduleExactAlarms()) {
            permissionDialogMessage =
                "精确闹钟权限被拒绝，可能无法准时收到倒计时完成提醒。请到设置中手动开启精确闹钟权限。"
            showPermissionDialog = true
        }
    }

    // 权限请求启动器
    val permissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderInfoRequest) }
    )

    // 检查定位权限和服务的函数
    fun checkLocationPermissionAndStart() {
        UnifiedPermissionHelper.checkLocationPermissionAndStart(
            context = context,
            permissionLauncher = permissionLauncher,
            onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderInfoRequest) }
        )
    }

    // 检查通知权限
    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13以下不需要运行时权限
        }
    }

    // 请求通知权限
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 请求精确闹钟权限
    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!countdownNotificationManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:${context.packageName}".toUri()
                }
                exactAlarmPermissionLauncher.launch(intent)
            }
        }
    }
    
    // 检查全屏Intent权限（Android 14+）
    fun checkFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            countdownNotificationManager.canUseFullScreenIntent()
        } else {
            true
        }
    }
    
    // 请求全屏Intent权限
    fun requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!checkFullScreenIntentPermission()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = "package:${context.packageName}".toUri()
                }
                exactAlarmPermissionLauncher.launch(intent)
            }
        }
    }

    // 检查所有必需权限
    fun checkAndRequestPermissions() {
        // 检查通知权限
        if (!checkNotificationPermission()) {
            requestNotificationPermission()
            return
        }

        // 检查精确闹钟权限
        if (!countdownNotificationManager.canScheduleExactAlarms()) {
            requestExactAlarmPermission()
            return
        }
        
        // 检查全屏Intent权限（Android 14+）
        if (!checkFullScreenIntentPermission()) {
            permissionDialogMessage = """
                为了在服务时间结束时能准时提醒您，需要开启「全屏通知」权限。
                
                请在设置中找到「全屏通知」或「显示在其他应用上层」选项并开启。
            """.trimIndent()
            showPermissionDialog = true
        }
    }

    // 处理结束服务的公共逻辑
    fun handleEndService(endType: Int) {
        Log.w("NavigationDebug", "ServiceCountdownScreen: handleEndService called with endType=$endType")
        Log.i("ServiceCountdownScreen", "========================================")
        Log.i("ServiceCountdownScreen", "🛑 开始处理结束服务 (endType=$endType)...")
        Log.i("ServiceCountdownScreen", "========================================")
        
        // 1. 停止倒计时前台服务
        CountdownForegroundService.stopCountdown(context)
        Log.i("ServiceCountdownScreen", "✅ 1. 已停止倒计时前台服务")

        // 2. 停止定位跟踪服务
        locationTrackingViewModel.onStopClicked()
        Log.i("ServiceCountdownScreen", "✅ 2. 已停止定位跟踪服务")

        // 3. 取消倒计时闹钟（使用订单ID精确取消）
        countdownNotificationManager.cancelCountdownAlarmForOrder(orderInfoRequest)
        Log.i("ServiceCountdownScreen", "✅ 3. 已取消倒计时闹钟 (orderId=${orderInfoRequest.orderId})")

        // 4. 停止响铃服务（如果正在响铃）
        AlarmRingtoneService.stopRingtone(context)
        Log.i("ServiceCountdownScreen", "✅ 4. 已停止响铃服务")

        // 5. 调用ViewModel结束服务（但不清除图片数据，保留给EndServiceSelectionScreen使用）
        countdownViewModel.endServiceWithoutClearingImages(orderInfoRequest, context)
        Log.i("ServiceCountdownScreen", "✅ 5. 已结束服务（保留图片数据）")

        // 6. 导航到结束服务选择页面
        navController.navigateToEndServiceSelection(
            orderParams = orderParams,
            endType = endType,
            projectIdList = projectIdList
        )
    }

    // 监听订单状态异常事件
    LaunchedEffect(orderStateError) {
        orderStateError?.let { stateModel ->
            // 构建错误提示信息
            orderStateErrorMessage = when (stateModel.state) {
                ServiceOrderStateModel.STATE_NOT_CREATED -> "订单未开单，无法继续服务"
                ServiceOrderStateModel.STATE_PENDING -> "订单状态异常：待执行"
                ServiceOrderStateModel.STATE_COMPLETED -> "订单已完成，无法继续服务"
                ServiceOrderStateModel.STATE_CANCELLED -> "订单已作废，无法继续服务"
                else -> stateModel.stateDesc ?: "订单状态异常，无法继续服务"
            }
            showOrderStateErrorDialog = true
        }
    }
    
    LaunchedEffect(orderInfoRequest) {
        sharedViewModel.getCachedOrderInfo(orderInfoRequest)
        sharedViewModel.getOrderInfo(orderInfoRequest)

        // 检查并启动定位服务
        checkLocationPermissionAndStart()

        // 恢复本地保存的图片数据
        countdownViewModel.loadUploadedImagesFromRepository(orderInfoRequest.toOrderKey())
        
        // 启动订单状态轮询（每5秒查询一次）
        countdownViewModel.startOrderStatePolling(orderInfoRequest.toOrderKey())

        // 监听图片上传结果
        navController.currentBackStackEntry?.savedStateHandle?.getStateFlow<Map<ImageTaskType, List<ImageTask>>?>(
            NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, null
        )?.collect { result ->
            result?.let {
                // 调用ViewModel处理图片上传结果
                countdownViewModel.handlePhotoUploadResult(orderInfoRequest, it)

                // 清除结果，避免重复处理
                navController.currentBackStackEntry?.savedStateHandle?.remove<Map<ImageTaskType, List<ImageTask>>>(
                    NavigationConstants.PHOTO_UPLOAD_RESULT_KEY
                )
            }
        }
    }

    // 初始化状态（使用data class统一管理）
    val initState = remember { mutableStateOf(CountdownInitState()) }
    
    // 计算服务信息的辅助函数
    fun calculateServiceInfo(orderInfo: ServiceOrderInfoModel): ServiceInfo {
        val selectedProjects = (orderInfo.projectList ?: emptyList())
            .filter { it.projectId in projectIdList }
        
        val serviceName = selectedProjects.joinToString(", ") { it.projectName }
        val totalMinutes = selectedProjects.sumOf { it.serviceTime }
        
        return ServiceInfo(serviceName, totalMinutes)
    }

    // 设置倒计时时间的通用函数
    fun setupCountdownTime() {
        val orderInfo = sharedViewModel.getCachedOrderInfo(orderInfoRequest) ?: return
        
        val serviceInfo = calculateServiceInfo(orderInfo)
        
        // 检查是否需要重新初始化
        val needsReinit = initState.value.lastProjectIdList != projectIdList ||
                         countdownState == ServiceCountdownState.ENDED ||
                         !initState.value.isInitialized

        if (!needsReinit || serviceInfo.totalMinutes <= 0) {
            return
        }

        // 首次初始化时检查权限（在设置倒计时之前）
        if (!initState.value.permissionsChecked) {
            checkAndRequestPermissions()
            initState.value = initState.value.copy(permissionsChecked = true)
        }

        // 设置ViewModel的倒计时（统一的时间计算逻辑）
        countdownViewModel.setCountdownTimeFromProjects(
            orderRequest = orderInfoRequest,
            projectList = orderInfo.projectList ?: emptyList(),
            selectedProjectIds = projectIdList
        )

        // 启动前台服务显示倒计时通知
        countdownViewModel.startForegroundService(
            context = context,
            request = orderInfoRequest,
            serviceName = serviceInfo.serviceName,
            totalSeconds = serviceInfo.totalMinutes * 60L
        )

        // 设置系统级倒计时闹钟（使用ViewModel计算的完成时间）
        val (state, remainingMillis, _) = countdownViewModel.getCurrentCountdownState()
        if (state == ServiceCountdownState.RUNNING && remainingMillis > 0) {
            val completionTime = System.currentTimeMillis() + remainingMillis
            countdownNotificationManager.scheduleCountdownAlarm(
                request = orderInfoRequest,
                serviceName = serviceInfo.serviceName,
                triggerTimeMillis = completionTime
            )
        }

        // 如果没有通知权限，显示提示
        if (!checkNotificationPermission()) {
            permissionDialogMessage = "通知权限被拒绝，可能无法收到倒计时完成提醒。请到设置中手动开启通知权限。"
            showPermissionDialog = true
        }

        // 更新初始化状态
        initState.value = initState.value.copy(
            isInitialized = true,
            lastProjectIdList = projectIdList
        )
    }

    // 初始设置倒计时时间
    LaunchedEffect(orderInfoRequest, projectIdList) {
        setupCountdownTime()
    }

    // 监听生命周期变化，在RESUMED状态下仅更新时间显示，不重新初始化
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 只在已初始化且未结束的情况下更新显示
            if (initState.value.isInitialized && countdownState != ServiceCountdownState.ENDED) {
                val orderInfo = sharedViewModel.getCachedOrderInfo(orderInfoRequest)
                orderInfo?.let {
                    // 仅刷新显示，不重新启动倒计时
                    countdownViewModel.refreshCountdownDisplay(
                        orderRequest = orderInfoRequest,
                        projectList = it.projectList ?: emptyList(),
                        selectedProjectIds = projectIdList
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("服务时间倒计时", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = singleClick { navController.navigateToHomeAndClearStack() }) {
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
                    navController = navController,
                    countdownState = countdownState,
                    formattedTime = formattedTime,
                    countdownViewModel = countdownViewModel,
                    orderParams = orderParams
                )

                Spacer(modifier = Modifier.height(24.dp))

                SelectedServicesCard(
                    orderInfoRequest = orderInfoRequest,
                    projectIdList = projectIdList,
                    sharedViewModel = sharedViewModel
                )

            }

            // 固定在底部的按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFF6F9FF).copy(alpha = 0.9f),
                                Color(0xFFF6F9FF)
                            ), startY = 0f, endY = 100f
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 32.dp)
            ) {
                Button(
                    onClick = singleClick {
                        // 验证照片是否已上传 (Mock模式下跳过验证)
                        if (!BuildConfig.USE_MOCK_DATA && !countdownViewModel.validatePhotosUploaded()) {
                            countdownViewModel.showToast("请上传照片")
                            return@singleClick
                        }

                        // 如果倒计时还在进行中，显示确认弹窗
                        if (countdownState == ServiceCountdownState.RUNNING) {
                            showConfirmDialog = true
                        } else {
                            handleEndService(1)
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
                            ServiceCountdownState.COMPLETED, ServiceCountdownState.OVERTIME -> Color(
                                0xFF4A90E2
                            ) // 蓝色（正常结束）
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
            }
        }
    }

    // 页面销毁时清理资源
    DisposableEffect(Unit) {
        onDispose {
            // 停止订单状态轮询
            countdownViewModel.stopOrderStatePolling()
            
            // 如果服务未正常结束，清理相关资源
            if (countdownState != ServiceCountdownState.ENDED) {
                // 1. 取消倒计时闹钟
                countdownNotificationManager.cancelCountdownAlarm()
                
                // 2. 停止响铃服务（如果正在响铃）
                AlarmRingtoneService.stopRingtone(context)
                
                // 注意：不停止前台服务和定位服务，因为用户可能只是退出页面
                // 服务应该继续在后台运行，直到用户主动结束服务
            }
        }
    }

    // 权限提示对话框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("权限提示") },
            text = { Text(permissionDialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = singleClick {
                        showPermissionDialog = false
                        // 根据权限类型跳转到对应设置页面
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && 
                            permissionDialogMessage.contains("全屏通知")) {
                            // Android 14+ 全屏Intent权限设置
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        } else {
                            // 通用应用设置页面
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        }
                        context.startActivity(intent)
                    }) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = singleClick { showPermissionDialog = false }) {
                    Text("稍后")
                }
            })
    }

    // 二次确认弹窗
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认提前结束服务") },
            text = { Text("服务时间尚未结束，确定要提前结束服务吗？") },
            confirmButton = {
                TextButton(
                    onClick = singleClick {
                        showConfirmDialog = false
                        handleEndService(2)  // 提前结束
                    }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = singleClick { showConfirmDialog = false }) {
                    Text("取消")
                }
            })
    }
    
    // 订单状态异常弹窗
    if (showOrderStateErrorDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭 */ },
            title = { Text("订单状态异常") },
            text = { Text(orderStateErrorMessage) },
            confirmButton = {
                TextButton(
                    onClick = singleClick {
                        showOrderStateErrorDialog = false
                        
                        Log.i("ServiceCountdownScreen", "========================================")
                        Log.i("ServiceCountdownScreen", "🛑 开始处理订单状态异常，停止所有服务...")
                        Log.i("ServiceCountdownScreen", "========================================")
                        
                        // 1. 清除错误状态
                        countdownViewModel.clearOrderStateError()
                        Log.i("ServiceCountdownScreen", "✅ 1. 已清除错误状态")
                        
                        // 2. 停止订单状态轮询
                        countdownViewModel.stopOrderStatePolling()
                        Log.i("ServiceCountdownScreen", "✅ 2. 已停止订单状态轮询")
                        
                        // 3. 停止倒计时前台服务
                        CountdownForegroundService.stopCountdown(context)
                        Log.i("ServiceCountdownScreen", "✅ 3. 已停止倒计时前台服务")
                        
                        // 4. 强制停止定位跟踪服务（使用forceStop确保停止）
                        locationTrackingViewModel.forceStop()
                        Log.i("ServiceCountdownScreen", "✅ 4. 已强制停止定位跟踪服务")
                        
                        // 5. 取消倒计时闹钟（使用订单ID精确取消）
                        countdownNotificationManager.cancelCountdownAlarmForOrder(orderInfoRequest)
                        Log.i("ServiceCountdownScreen", "✅ 5. 已取消倒计时闹钟 (orderId=${orderInfoRequest.orderId})")
                        
                        // 6. 停止响铃服务（如果正在响铃）
                        AlarmRingtoneService.stopRingtone(context)
                        Log.i("ServiceCountdownScreen", "✅ 6. 已停止响铃服务")
                        
                        // 7. 清理ViewModel状态和本地数据（不清除图片数据，因为订单可能需要重新开始）
                        countdownViewModel.endServiceWithoutClearingImages(orderInfoRequest, context)
                        Log.i("ServiceCountdownScreen", "✅ 7. 已清理ViewModel状态")
                        
                        Log.i("ServiceCountdownScreen", "========================================")
                        Log.i("ServiceCountdownScreen", "✅ 所有服务已停止，准备返回首页")
                        Log.i("ServiceCountdownScreen", "========================================")
                        
                        // 8. 返回首页
                        navController.navigateToHomeAndClearStack()
                    }) {
                    Text("确定")
                }
            })
    }
}

@Composable
fun CountdownTimerCard(
    navController: NavController,
    countdownState: ServiceCountdownState,
    formattedTime: String = "12:00:00",
    countdownViewModel: ServiceCountdownViewModel,
    orderParams: OrderNavParams
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
                onClick = singleClick {
                    val existingImages = countdownViewModel.getCurrentUploadedImages()
                    // 通过savedStateHandle传递已有的图片数据
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        NavigationConstants.EXISTING_IMAGES_KEY, existingImages
                    )
                    navController.navigateToPhotoUpload(orderParams = orderParams)
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
    orderInfoRequest: OrderInfoRequestModel,
    projectIdList: List<Int>,
    sharedViewModel: SharedOrderDetailViewModel
) {
    val tagHeightEstimate = 32.dp
    val tagOverlap = 12.dp

    val orderInfo = sharedViewModel.getCachedOrderInfo(orderInfoRequest)
    val allProjects = orderInfo?.projectList ?: emptyList()
    val isAllSelected =
        projectIdList.isEmpty() || (allProjects.isNotEmpty() && projectIdList.containsAll(
            allProjects.map { it.projectId }))
    val selectedProjects =
        if (isAllSelected) allProjects else allProjects.filter { it.projectId in projectIdList }

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
        orderInfoRequest = OrderInfoRequestModel(orderId = 12345L, planId = 0),
        projectIdList = listOf(1, 2),
        sharedViewModel = hiltViewModel()
    )
}