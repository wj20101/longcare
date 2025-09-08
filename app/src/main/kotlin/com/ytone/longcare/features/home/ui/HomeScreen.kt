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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.home.vm.HomeViewModel
import com.ytone.longcare.features.maindashboard.ui.MainDashboardScreen
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
                    else -> permission
                }
            }
            permissionDeniedMessage = "应用需要以下权限才能正常工作：${deniedPermissionNames.joinToString("、")}"
            showPermissionDialog = true
        }
    }

    // 检查并请求权限
    LaunchedEffect(Unit) {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
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
                        val requiredPermissions = arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CAMERA
                        )
                        permissionLauncher.launch(requiredPermissions)
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
