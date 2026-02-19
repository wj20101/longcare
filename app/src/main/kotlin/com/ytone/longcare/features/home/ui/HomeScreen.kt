package com.ytone.longcare.features.home.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.PermissionGuideType
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.shared.vm.TodayOrderViewModel

@Composable
fun HomeScreen(
    actions: HomeActions,
    homeSharedViewModel: HomeSharedViewModel = hiltViewModel(),
    todayOrderViewModel: TodayOrderViewModel = hiltViewModel()
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf("") }

    var showPopupPermissionDialog by remember { mutableStateOf(false) }
    var popupPermissionMessage by remember { mutableStateOf("") }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var batteryMessage by remember { mutableStateOf("") }

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

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        when (DeviceCompatibilityHelper.getRequiredPermissionGuide(context)) {
            PermissionGuideType.BATTERY -> {
                batteryMessage = DeviceCompatibilityHelper.getBatteryGuideMessage()
                showBatteryDialog = true
            }

            else -> Unit
        }
    }

    LaunchedEffect(Unit) {
        val missingPermissions = buildRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

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
                batteryMessage = DeviceCompatibilityHelper.getBatteryGuideMessage()
                showBatteryDialog = true
            }

            else -> Unit
        }
    }

    HomeScreenPermissionDialogs(
        showPermissionDialog = showPermissionDialog,
        permissionDeniedMessage = permissionDeniedMessage,
        onDismissPermissionDialog = { showPermissionDialog = false },
        onRetryPermissionRequest = {
            showPermissionDialog = false
            permissionLauncher.launch(buildRequiredPermissions().toTypedArray())
        },
        showPopupPermissionDialog = showPopupPermissionDialog,
        popupPermissionMessage = popupPermissionMessage,
        onDismissPopupPermissionDialog = { showPopupPermissionDialog = false },
        onOpenPopupSettings = {
            showPopupPermissionDialog = false
            val currentGuide = DeviceCompatibilityHelper.getRequiredPermissionGuide(context)
            DeviceCompatibilityHelper.markPermissionGuideShown(context, currentGuide)

            val intent = if (DeviceCompatibilityHelper.needsSpecialAdaptation()) {
                DeviceCompatibilityHelper.getPopupPermissionIntent(context)
            } else {
                DeviceCompatibilityHelper.getOverlayPermissionIntent(context)
            }
            overlayPermissionLauncher.launch(intent)
        },
        onSkipPopupGuide = {
            showPopupPermissionDialog = false
            val currentGuide = DeviceCompatibilityHelper.getRequiredPermissionGuide(context)
            DeviceCompatibilityHelper.markPermissionGuideShown(context, currentGuide)

            when (DeviceCompatibilityHelper.getRequiredPermissionGuide(context)) {
                PermissionGuideType.BATTERY -> {
                    batteryMessage = DeviceCompatibilityHelper.getBatteryGuideMessage()
                    showBatteryDialog = true
                }

                else -> Unit
            }
        },
        showBatteryDialog = showBatteryDialog,
        batteryMessage = batteryMessage,
        onDismissBatteryDialog = { showBatteryDialog = false },
        onOpenBatterySettings = {
            showBatteryDialog = false
            DeviceCompatibilityHelper.markPermissionGuideShown(context, PermissionGuideType.BATTERY)
            val intent = DeviceCompatibilityHelper.getBatteryOptimizationIntent(context)
            DeviceCompatibilityHelper.safeStartActivity(context, intent)
        },
        onAcknowledgeBatteryGuide = {
            showBatteryDialog = false
            DeviceCompatibilityHelper.markPermissionGuideShown(context, PermissionGuideType.BATTERY)
        }
    )

    HomeScreenPagerContent(
        actions = actions,
        homeSharedViewModel = homeSharedViewModel,
        todayOrderViewModel = todayOrderViewModel
    )
}
