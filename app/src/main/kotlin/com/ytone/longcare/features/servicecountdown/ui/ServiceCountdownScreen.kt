package com.ytone.longcare.features.servicecountdown.ui

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.model.OrderKey

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
    

    val launchers = rememberServiceCountdownScreenLaunchers(
        countdownViewModel = countdownViewModel,
        locationTrackingViewModel = locationTrackingViewModel,
        orderKey = orderKey,
        onPermissionDialogRequired = { message ->
            permissionDialogMessage = message
            showPermissionDialog = true
        }
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
        notificationPermissionLauncher = launchers.notificationPermissionLauncher,
        exactAlarmPermissionLauncher = launchers.exactAlarmPermissionLauncher,
        permissionLauncher = launchers.locationPermissionLauncher,
        onPermissionDialogRequired = { message ->
            permissionDialogMessage = message
            showPermissionDialog = true
        },
        onOrderStateError = { message ->
            orderStateErrorMessage = message
            showOrderStateErrorDialog = true
        }
    )

    ServiceCountdownScreenScaffold(
        countdownState = countdownState,
        formattedTime = formattedTime,
        orderKey = orderKey,
        projectIdList = projectIdList,
        sharedViewModel = sharedViewModel,
        onNavigateBack = actions.onNavigateHomeAndClearStack,
        onOpenPhotoUpload = {
            val existingImages = countdownViewModel.getCurrentUploadedImages()
            actions.onNavigateToPhotoUpload(orderKey, existingImages)
        },
        onBottomActionClick = {
            handleServiceCountdownBottomAction(
                countdownState = countdownState,
                isMockDataEnabled = countdownViewModel.isMockDataEnabled,
                validatePhotosUploaded = countdownViewModel::validatePhotosUploaded,
                onShowToast = countdownViewModel::showToast,
                onRequireConfirm = { showConfirmDialog = true },
                onEndServiceDirectly = {
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
            )
        }
    )

    ServiceCountdownDialogsHost(
        showPermissionDialog = showPermissionDialog,
        permissionDialogMessage = permissionDialogMessage,
        onDismissPermissionDialog = { showPermissionDialog = false },
        showConfirmDialog = showConfirmDialog,
        onDismissConfirmDialog = { showConfirmDialog = false },
        showOrderStateErrorDialog = showOrderStateErrorDialog,
        orderStateErrorMessage = orderStateErrorMessage,
        onDismissOrderStateErrorDialog = { showOrderStateErrorDialog = false },
        context = context,
        orderKey = orderKey,
        projectIdList = projectIdList,
        countdownViewModel = countdownViewModel,
        locationTrackingViewModel = locationTrackingViewModel,
        actions = actions
    )
}
