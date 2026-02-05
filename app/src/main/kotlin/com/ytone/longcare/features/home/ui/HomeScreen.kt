package com.ytone.longcare.features.home.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.PermissionGuideType
import com.ytone.longcare.features.home.vm.HomeViewModel
import com.ytone.longcare.features.maindashboard.ui.MainDashboardScreen
import dagger.hilt.android.EntryPointAccessors
import com.ytone.longcare.di.HomeEntryPoint
import com.ytone.longcare.features.nursing.ui.NursingScreen
import com.ytone.longcare.features.profile.ui.ProfileScreen
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.theme.bgGradientBrush
import kotlinx.coroutines.launch


@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf("") }
    
    // 厂商设备弹窗权限引导状态
    var showPopupPermissionDialog by remember { mutableStateOf(false) }
    var popupPermissionMessage by remember { mutableStateOf("") }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var batteryMessage by remember { mutableStateOf("") }

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filterValues { !it }.keys
        if (deniedPermissions.isNotEmpty()) {
            val deniedPermissionNames = deniedPermissions.map { permission ->
                when (permission) {
                    Manifest.permission.ACCESS_FINE_LOCATION -> "精确定位"
                    Manifest.permission.CAMERA -> "拍照"
                    Manifest.permission.POST_NOTIFICATIONS -> "通知提醒"
                    else -> permission
                }
            }
            permissionDeniedMessage = "应用需要以下权限才能正常工作：${deniedPermissionNames.joinToString("、")}"
            showPermissionDialog = true
        }
    }
    
    // 悬浮窗/厂商权限设置返回监听
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 用户从设置页返回，检查是否还需要其他权限引导
        when (DeviceCompatibilityHelper.getRequiredPermissionGuide(context)) {
            PermissionGuideType.BATTERY -> {
                val batteryGuide = DeviceCompatibilityHelper.getBatteryGuideMessage()
                if (batteryGuide != null) {
                    batteryMessage = batteryGuide
                    showBatteryDialog = true
                }
            }
            else -> { /* 不需要更多引导 */ }
        }
    }

    // 检查并请求权限
    LaunchedEffect(Unit) {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        
        // Android 13 (API 33) 及以上版本添加通知权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
        
        // 检查需要引导的权限类型（按优先级：悬浮窗 -> 厂商权限 -> 省电策略）
        when (DeviceCompatibilityHelper.getRequiredPermissionGuide(context)) {
            PermissionGuideType.OVERLAY -> {
                popupPermissionMessage = """
                    为保证服务结束时能弹出全屏提醒，请开启悬浮窗权限：
                    
                    点击「去设置」后，找到本应用并开启「显示在其他应用上层」权限。
                """.trimIndent()
                showPopupPermissionDialog = true
            }
            PermissionGuideType.MANUFACTURER_POPUP -> {
                val popupGuide = DeviceCompatibilityHelper.getPopupPermissionGuideMessage()
                if (popupGuide != null) {
                    popupPermissionMessage = popupGuide
                    showPopupPermissionDialog = true
                }
            }
            PermissionGuideType.BATTERY -> {
                val batteryGuide = DeviceCompatibilityHelper.getBatteryGuideMessage()
                if (batteryGuide != null) {
                    batteryMessage = batteryGuide
                    showBatteryDialog = true
                }
            }
            else -> { /* 不需要引导 */ }
        }
    }

    // 权限被拒绝时的对话框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("权限请求") },
            text = { Text(permissionDeniedMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        // 重新请求权限
                        val requiredPermissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CAMERA
                        )
                        // Android 13+ 添加通知权限
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    }
                ) {
                    Text("重新授权")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("稍后再说")
                }
            }
        )
    }
    
    // 弹窗权限引导弹窗（悬浮窗或厂商弹窗权限）
    if (showPopupPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPopupPermissionDialog = false },
            title = { Text("开启弹窗权限") },
            text = { Text(popupPermissionMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPopupPermissionDialog = false
                        // 标记当前权限引导已显示
                        val currentGuide = DeviceCompatibilityHelper.getRequiredPermissionGuide(context)
                        DeviceCompatibilityHelper.markPermissionGuideShown(context, currentGuide)
                        
                        // 跳转到对应的设置页
                        val intent = if (DeviceCompatibilityHelper.needsSpecialAdaptation()) {
                            DeviceCompatibilityHelper.getPopupPermissionIntent(context)
                        } else {
                            DeviceCompatibilityHelper.getOverlayPermissionIntent(context)
                        }
                        overlayPermissionLauncher.launch(intent)
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPopupPermissionDialog = false
                        // 标记当前权限引导已显示（跳过也算显示过）
                        val currentGuide = DeviceCompatibilityHelper.getRequiredPermissionGuide(context)
                        DeviceCompatibilityHelper.markPermissionGuideShown(context, currentGuide)
                        
                        // 检查下一个需要引导的权限
                        when (DeviceCompatibilityHelper.getRequiredPermissionGuide(context)) {
                            PermissionGuideType.BATTERY -> {
                                val batteryGuide = DeviceCompatibilityHelper.getBatteryGuideMessage()
                                if (batteryGuide != null) {
                                    batteryMessage = batteryGuide
                                    showBatteryDialog = true
                                }
                            }
                            else -> { /* 不需要更多引导 */ }
                        }
                    }
                ) {
                    Text("跳过")
                }
            }
        )
    }
    
    // 省电策略引导弹窗
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("设置省电策略") },
            text = { Text(batteryMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        DeviceCompatibilityHelper.markPermissionGuideShown(context, PermissionGuideType.BATTERY)
                        // 跳转到省电策略设置
                        val intent = DeviceCompatibilityHelper.getBatteryOptimizationIntent(context)
                        DeviceCompatibilityHelper.safeStartActivity(context, intent)
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        DeviceCompatibilityHelper.markPermissionGuideShown(context, PermissionGuideType.BATTERY)
                    }
                ) {
                    Text("我知道了")
                }
            }
        )
    }

    val bottomNavItems = listOf(
        CustomBottomNavigationItem("首页"),
        CustomBottomNavigationItem("护理工作"),
        CustomBottomNavigationItem("我的")
    )
    val pagerState = rememberPagerState(pageCount = { bottomNavItems.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgGradientBrush)
    ){
        Scaffold(
            bottomBar = {
                AppBottomNavigation(
                    items = bottomNavItems,
                    selectedItemIndex = pagerState.currentPage,
                    onItemSelected = {
                        coroutineScope.launch { pagerState.scrollToPage(it) }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> MainDashboardScreen(navController = navController)
                    1 -> NursingScreen(navController = navController)
                    2 -> ProfileScreen(navController = navController)
                }
            }
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    LongCareTheme {
        HomeScreen(navController = rememberNavController())
    }
}
