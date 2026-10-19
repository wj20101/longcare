package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
import com.ytone.longcare.common.utils.showShortToast
import com.ytone.longcare.core.ui.permission.BackgroundSettingsDialog
import com.ytone.longcare.core.ui.R as CoreUiR
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.model.OrderKey
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R

/**
 * 服务倒计时页面
 * 
 * 功能：
 * 1. 显示服务倒计时和超时计时
 * 2. 管理系统闹钟
 * 3. 处理照片上传和定位追踪
 * 4. 支持提前结束和正常结束服务
 * 
 * 优化点：
 * - 使用统一的时间计算逻辑，确保UI、通知、闹钟时间一致
 * - 可见时按持久服务起点同步显示和系统闹钟
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
    // 统一处理系统返回键，确保与导航按钮行为一致
    CustomBackHandler(customAction = actions.onNavigateHomeAndClearStack)

    // 从ViewModel获取状态
    val countdownState by countdownViewModel.countdownState.collectAsStateWithLifecycle()
    val formattedTime by countdownViewModel.formattedTime.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val photoRequiredMessage = stringResource(R.string.service_countdown_photo_required)
    
    // 订单状态异常弹窗状态
    var showOrderStateErrorDialog by remember { mutableStateOf(false) }
    var orderStateErrorMessage by remember { mutableStateOf("") }

    // 二次确认弹窗状态
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 权限相关状态
    var permissionIssue by rememberSaveable(orderKey) { mutableStateOf<ServiceCountdownPermissionIssue?>(null) }
    var showBackgroundSettings by rememberSaveable(orderKey) { mutableStateOf(false) }

    val launchers = rememberServiceCountdownScreenLaunchers(
        orderKey = orderKey,
        canScheduleExactAlarms = countdownViewModel::canScheduleExactAlarms,
        canUseFullScreenIntent = countdownViewModel::canUseFullScreenIntent,
        onLocationPermissionGranted = { locationTrackingViewModel.startTracking(orderKey) },
        onPermissionDialogRequired = { issue ->
            permissionIssue = issue
        }
    )

    ServiceCountdownInitialPermissionCheck(orderKey, launchers.checkPermissions)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Saved state preserves the dialog choice, never a permission grant conclusion.
        if (permissionIssue != null) {
            permissionIssue = requiredCountdownPermissionIssue(
                context,
                countdownViewModel.canScheduleExactAlarms(),
                countdownViewModel.canUseFullScreenIntent(),
            )
        }
    }

    ServiceCountdownLifecycleEffects(
        actions = actions,
        orderKey = orderKey,
        projectIdList = projectIdList,
        sharedViewModel = sharedViewModel,
        countdownViewModel = countdownViewModel,
        locationTrackingViewModel = locationTrackingViewModel,
        context = context,
        lifecycleOwner = lifecycleOwner,
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
        onOpenBackgroundSettings = {
            showBackgroundSettings = true
        },
        onOpenPhotoUpload = {
            val existingImages = countdownViewModel.getCurrentUploadedImages()
            actions.onNavigateToPhotoUpload(orderKey, existingImages)
        },
        onBottomActionClick = {
            handleServiceCountdownBottomAction(
                countdownState = countdownState,
                validatePhotosUploaded = countdownViewModel::validatePhotosUploaded,
                photoRequiredMessage = photoRequiredMessage,
                onShowToast = context::showShortToast,
                onRequireConfirm = { showConfirmDialog = true },
                onEndServiceDirectly = {
                    handleEndService(
                        orderKey = orderKey,
                        projectIdList = projectIdList,
                        actions = actions,
                        endType = 1
                    )
                }
            )
        }
    )

    if (showBackgroundSettings) {
        val initialGuides = remember(context) { DeviceCompatibilityHelper.getAllGuides(context) }
        val permissionsEnabledMessage = stringResource(CoreUiR.string.background_settings_system_permissions_enabled)
        BackgroundSettingsDialog(
            initialItems = initialGuides,
            refreshItems = { DeviceCompatibilityHelper.getAllGuides(context) },
            onItemAction = { DeviceCompatibilityHelper.openGuide(context, it) },
            onCheckSystemPermissions = {
                showBackgroundSettings = false
                permissionIssue = requiredCountdownPermissionIssue(
                    context,
                    countdownViewModel.canScheduleExactAlarms(),
                    countdownViewModel.canUseFullScreenIntent(),
                )
                if (permissionIssue == null) {
                    context.showShortToast(permissionsEnabledMessage)
                }
            },
            onDismiss = {
                if (permissionIssue != null) {
                    permissionIssue = requiredCountdownPermissionIssue(
                        context,
                        countdownViewModel.canScheduleExactAlarms(),
                        countdownViewModel.canUseFullScreenIntent(),
                    )
                }
                showBackgroundSettings = false
            },
        )
    }

    ServiceCountdownDialogsHost(
        permissionIssue = permissionIssue.takeUnless { showBackgroundSettings },
        onDismissPermissionDialog = {
            permissionIssue = null
        },
        showConfirmDialog = showConfirmDialog,
        onDismissConfirmDialog = { showConfirmDialog = false },
        showOrderStateErrorDialog = showOrderStateErrorDialog,
        orderStateErrorMessage = orderStateErrorMessage,
        onDismissOrderStateErrorDialog = { showOrderStateErrorDialog = false },
        onOpenPermissionSettings = launchers.openPermissionSettings,
        onRequestPermission = launchers.requestPermission,
        orderKey = orderKey,
        projectIdList = projectIdList,
        countdownViewModel = countdownViewModel,
        actions = actions
    )
}
