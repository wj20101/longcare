package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.navigateToNfcSignInForEndOrder
import com.ytone.longcare.navigation.navigateToPhotoUpload
import com.ytone.longcare.navigation.navigateToHomeAndClearStack
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import androidx.core.net.toUri
import com.ytone.longcare.common.utils.HomeBackHandler
import com.ytone.longcare.di.ServiceCountdownEntryPoint
import dagger.hilt.android.EntryPointAccessors


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
    orderInfoRequest: OrderInfoRequestModel,
    projectIdList: List<String>,
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel(),
    countdownViewModel: ServiceCountdownViewModel = hiltViewModel(),
    locationTrackingViewModel: LocationTrackingViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    
    // 统一处理系统返回键，确保与导航按钮行为一致
    HomeBackHandler(navController = navController)

    // 从ViewModel获取状态
    val countdownState by countdownViewModel.countdownState.collectAsStateWithLifecycle()
    val formattedTime by countdownViewModel.formattedTime.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 获取CountdownNotificationManager实例
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        ServiceCountdownEntryPoint::class.java
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
            permissionDialogMessage = "通知权限被拒绝，可能无法收到倒计时完成提醒。请到设置中手动开启通知权限。"
            showPermissionDialog = true
        }
    }
    
    // 精确闹钟权限请求启动器
    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 检查权限是否已授予
        if (!countdownNotificationManager.canScheduleExactAlarms()) {
            permissionDialogMessage = "精确闹钟权限被拒绝，可能无法准时收到倒计时完成提醒。请到设置中手动开启精确闹钟权限。"
            showPermissionDialog = true
        }
    }

    // 权限请求启动器
    val permissionLauncher = rememberLocationPermissionLauncher(
        locationTrackingViewModel = locationTrackingViewModel, orderId = orderInfoRequest.orderId
    )

    // 检查定位权限和服务的函数
    fun checkLocationPermissionAndStart() {
        UnifiedPermissionHelper.checkLocationPermissionAndStart(context, permissionLauncher)
    }
    
    // 检查通知权限
    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
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
    }
    
    // 处理结束服务的公共逻辑
    fun handleEndService(endType: Int) {
        countdownViewModel.endService(orderInfoRequest, context)
        // 取消倒计时闹钟
        countdownNotificationManager.cancelCountdownAlarm()
        
        val uploadedImages = countdownViewModel.getCurrentUploadedImages()
        val beginImgList = uploadedImages[ImageTaskType.BEFORE_CARE]?.mapNotNull { it.key } ?: emptyList()
        val endImgList = uploadedImages[ImageTaskType.AFTER_CARE]?.mapNotNull { it.key } ?: emptyList()

        navController.navigateToNfcSignInForEndOrder(
            orderInfoRequest = orderInfoRequest,
            params = EndOderInfo(
                projectIdList = projectIdList.map { it.toInt() },
                beginImgList = beginImgList,
                endImgList = endImgList,
                endType = endType
            ),
        )
    }

    LaunchedEffect(orderInfoRequest) {
        sharedViewModel.getCachedOrderInfo(orderInfoRequest)
        sharedViewModel.getOrderInfo(orderInfoRequest)

        // 检查并启动定位服务
        checkLocationPermissionAndStart()
        
        // 恢复本地保存的图片数据
        countdownViewModel.loadUploadedImagesFromLocal(orderInfoRequest)

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

    // 状态跟踪变量
    var isCountdownInitialized by remember { mutableStateOf(false) }
    var lastSetupTime by remember { mutableLongStateOf(0L) }
    var lastProjectIdList by remember { mutableStateOf(emptyList<Int>()) }
    var permissionsChecked by remember { mutableStateOf(false) }
    
    // 防抖延迟时间（毫秒）
    val debounceDelay = 500L
    
    // 设置倒计时时间的通用函数
    fun setupCountdownTime() {
        val currentTime = System.currentTimeMillis()
        
        // 防抖检查：如果距离上次调用时间太短，则跳过
        if (currentTime - lastSetupTime < debounceDelay) {
            return
        }
        
        val orderInfo = sharedViewModel.getCachedOrderInfo(orderInfoRequest)
        orderInfo?.let {
            // 计算总服务时间（分钟）
            val totalMinutes = (it.projectList ?: emptyList())
                .filter { project -> project.projectId in projectIdList.map { it.toInt() } }
                .sumOf { project -> project.serviceTime }
            
            // 检查是否需要重新初始化
            // 允许在锁屏解锁后重新刷新倒计时状态，确保显示正确的超时状态
            val needsReinit = lastProjectIdList != projectIdList.map { it.toInt() } ||
                             countdownState == ServiceCountdownState.ENDED ||
                             !isCountdownInitialized
            
            if (needsReinit && totalMinutes > 0) {
                // 首次初始化时检查权限
                if (!permissionsChecked) {
                    checkAndRequestPermissions()
                    permissionsChecked = true
                }
                
                // 设置ViewModel的倒计时
                countdownViewModel.setCountdownTimeFromProjects(
                    orderRequest = orderInfoRequest,
                    projectList = it.projectList ?: emptyList(),
                    selectedProjectIds = projectIdList.map { it.toInt() }
                )
                
                // 启动前台服务显示倒计时通知
                val serviceName = (it.projectList ?: emptyList())
                    .filter { project -> project.projectId in projectIdList.map { it.toInt() } }
                    .joinToString(", ") { project -> project.projectName }
                val totalSeconds = totalMinutes * 60L
                
                countdownViewModel.startForegroundService(
                    context = context,
                    orderId = orderInfoRequest.orderId,
                    serviceName = serviceName,
                    totalSeconds = totalSeconds
                )
                
                // 设置系统级倒计时闹钟（只有在权限充足时）
                if (checkNotificationPermission() && countdownNotificationManager.canScheduleExactAlarms()) {

                    val completionTime = countdownNotificationManager.calculateCompletionTime(totalMinutes * 60 * 1000L)
                    countdownNotificationManager.scheduleCountdownAlarm(
                        orderId = orderInfoRequest.orderId,
                        serviceName = serviceName,
                        triggerTimeMillis = completionTime
                    )
                } else {
                    // 权限不足时显示提示
                    permissionDialogMessage = "缺少必要权限，可能无法收到倒计时完成提醒。请检查通知权限和精确闹钟权限设置。"
                    showPermissionDialog = true
                }
                
                // 更新状态
                isCountdownInitialized = true
                lastProjectIdList = projectIdList.map { it.toInt() }
            }
            
            lastSetupTime = currentTime
        }
    }

    // 初始设置倒计时时间
    LaunchedEffect(orderInfoRequest, projectIdList) {
        setupCountdownTime()
    }

    // 监听生命周期变化，在RESUMED状态下重新计算倒计时
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 在RESUMED状态下强制重新设置倒计时，忽略防抖机制
            // 确保锁屏解锁后状态正确，显示当前真实的倒计时状态
            val orderInfo = sharedViewModel.getCachedOrderInfo(orderInfoRequest)
            orderInfo?.let {
                // 计算总服务时间（分钟）
                val totalMinutes = (it.projectList ?: emptyList())
                    .filter { project -> project.projectId in projectIdList.map { it.toInt() } }
                    .sumOf { project -> project.serviceTime }
                
                if (totalMinutes > 0) {
                    // 强制重新计算倒计时状态，不受防抖限制
                    countdownViewModel.setCountdownTimeFromProjects(
                        orderRequest = orderInfoRequest,
                        projectList = it.projectList ?: emptyList(),
                        selectedProjectIds = projectIdList.map { it.toInt() }
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
                countdownState = countdownState,
                formattedTime = formattedTime,
                countdownViewModel = countdownViewModel,
                orderInfoRequest = orderInfoRequest
            )

            Spacer(modifier = Modifier.height(24.dp))

            SelectedServicesCard(
                orderInfoRequest = orderInfoRequest, projectIdList = projectIdList.map { it.toString() }, sharedViewModel = sharedViewModel
            )

            Spacer(modifier = Modifier.weight(1f))

            // End Service Button
            Button(
                onClick = {
                    // 验证照片是否已上传
                    if (!countdownViewModel.validatePhotosUploaded()) {
                        countdownViewModel.showToast("请上传照片")
                        return@Button
                    }

                    // 如果倒计时还在进行中，显示确认弹窗
                    if (countdownState == ServiceCountdownState.RUNNING) {
                        showConfirmDialog = true
                    } else {
                        // 直接结束服务
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
    
    // 页面销毁时清理资源
    DisposableEffect(Unit) {
        onDispose {
            // 如果服务未正常结束，取消倒计时闹钟
            if (countdownState != ServiceCountdownState.ENDED) {
                countdownNotificationManager.cancelCountdownAlarm()
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
                    onClick = {
                        showPermissionDialog = false
                        // 引导用户到设置页面
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("稍后")
                }
            }
        )
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
                        handleEndService(2)  // 提前结束
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
    countdownState: ServiceCountdownState,
    formattedTime: String = "12:00:00",
    countdownViewModel: ServiceCountdownViewModel,
    orderInfoRequest: OrderInfoRequestModel
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
                    val existingImages = countdownViewModel.getCurrentUploadedImages()
                    // 通过savedStateHandle传递已有的图片数据
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        NavigationConstants.EXISTING_IMAGES_KEY, existingImages
                    )
                    navController.navigateToPhotoUpload(orderInfoRequest = orderInfoRequest)
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
    orderInfoRequest: OrderInfoRequestModel, projectIdList: List<String>, sharedViewModel: SharedOrderDetailViewModel
) {
    val tagHeightEstimate = 32.dp
    val tagOverlap = 12.dp

    // 获取订单详情
    val orderInfo = sharedViewModel.getCachedOrderInfo(orderInfoRequest)
    val allProjects = orderInfo?.projectList ?: emptyList()

    // 判断是否为全选状态
    val isAllSelected = projectIdList.isEmpty() || 
        (allProjects.isNotEmpty() && projectIdList.containsAll(allProjects.map { it.projectId.toString() }))

    // 根据是否全选来确定显示的项目
    val selectedProjects = if (isAllSelected) {
        allProjects
    } else {
        allProjects.filter { it.projectId.toString() in projectIdList }
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
        orderInfoRequest = OrderInfoRequestModel(orderId = 12345L, planId = 0), projectIdList = listOf("1", "2"), sharedViewModel = hiltViewModel()
    )
}