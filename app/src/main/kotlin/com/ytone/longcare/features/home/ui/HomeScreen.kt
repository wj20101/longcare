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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.common.utils.BatteryGuideStep
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf("") }

    var showPopupPermissionDialog by remember { mutableStateOf(false) }
    var popupPermissionMessage by remember { mutableStateOf("") }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var batteryDialogTitle by remember { mutableStateOf("") }
    var batteryMessage by remember { mutableStateOf("") }
    var batteryConfirmLabel by remember { mutableStateOf("去设置") }
    var batteryGuideStep by remember { mutableStateOf(BatteryGuideStep.NONE) }

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
        val batteryStep = DeviceCompatibilityHelper.getBatteryGuideStep(context)
        if (batteryStep != BatteryGuideStep.NONE) {
            batteryGuideStep = batteryStep
            batteryDialogTitle = DeviceCompatibilityHelper.getBatteryGuideDialogTitle(batteryStep)
            batteryMessage = DeviceCompatibilityHelper.getBatteryGuideMessage(context, batteryStep)
            batteryConfirmLabel = DeviceCompatibilityHelper.getBatteryGuideConfirmLabel(batteryStep)
            showBatteryDialog = true
        }
    }

    fun refreshCompatibilityGuides() {
        when (DeviceCompatibilityHelper.getRequiredPermissionGuide(context)) {
            PermissionGuideType.OVERLAY -> {
                batteryGuideStep = BatteryGuideStep.NONE
                showBatteryDialog = false
                popupPermissionMessage = """
                    为保证服务结束时能弹出全屏提醒，请开启悬浮窗权限：
                    
                    点击「去设置」后，找到本应用并开启「显示在其他应用上层」权限。
                """.trimIndent()
                showPopupPermissionDialog = true
            }

            PermissionGuideType.MANUFACTURER_POPUP -> {
                batteryGuideStep = BatteryGuideStep.NONE
                showBatteryDialog = false
                DeviceCompatibilityHelper.getPopupPermissionGuideMessage()?.let { popupGuide ->
                    popupPermissionMessage = popupGuide
                    showPopupPermissionDialog = true
                }
            }

            PermissionGuideType.BATTERY -> {
                showPopupPermissionDialog = false
                val step = DeviceCompatibilityHelper.getBatteryGuideStep(context)
                if (step != BatteryGuideStep.NONE) {
                    batteryGuideStep = step
                    batteryDialogTitle = DeviceCompatibilityHelper.getBatteryGuideDialogTitle(step)
                    batteryMessage = DeviceCompatibilityHelper.getBatteryGuideMessage(context, step)
                    batteryConfirmLabel = DeviceCompatibilityHelper.getBatteryGuideConfirmLabel(step)
                    showBatteryDialog = true
                }
            }

            else -> {
                batteryGuideStep = BatteryGuideStep.NONE
                showBatteryDialog = false
                showPopupPermissionDialog = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val missingPermissions = buildRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

        refreshCompatibilityGuides()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshCompatibilityGuides()
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
            refreshCompatibilityGuides()
        },
        showBatteryDialog = showBatteryDialog,
        batteryDialogTitle = batteryDialogTitle,
        batteryMessage = batteryMessage,
        onDismissBatteryDialog = { showBatteryDialog = false },
        onConfirmBatteryGuide = {
            showBatteryDialog = false
            when (batteryGuideStep) {
                BatteryGuideStep.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                    DeviceCompatibilityHelper.markIgnoreBatteryOptimizationRequestAttempted(context)
                    DeviceCompatibilityHelper.safeStartActivity(
                        context,
                        DeviceCompatibilityHelper.getRequestIgnoreBatteryOptimizationIntent(context)
                    )
                }

                BatteryGuideStep.OPEN_BATTERY_SETTINGS -> {
                    DeviceCompatibilityHelper.safeStartActivity(
                        context,
                        DeviceCompatibilityHelper.getBatteryOptimizationIntent(context)
                    )
                }

                BatteryGuideStep.OPEN_AUTO_START_SETTINGS -> {
                    DeviceCompatibilityHelper.markAutoStartGuideShown(context)
                    val intent =
                        DeviceCompatibilityHelper.getAutoStartIntent(context)
                            ?: DeviceCompatibilityHelper.getAppSettingsIntent(context)
                    DeviceCompatibilityHelper.safeStartActivity(context, intent)
                }

                BatteryGuideStep.NONE -> Unit
            }
        },
        batteryConfirmLabel = batteryConfirmLabel,
        onAcknowledgeBatteryGuide = {
            showBatteryDialog = false
            if (batteryGuideStep == BatteryGuideStep.OPEN_AUTO_START_SETTINGS) {
                DeviceCompatibilityHelper.markAutoStartGuideShown(context)
            }
        }
    )

    HomeScreenPagerContent(
        actions = actions,
        homeSharedViewModel = homeSharedViewModel,
        todayOrderViewModel = todayOrderViewModel
    )
}
