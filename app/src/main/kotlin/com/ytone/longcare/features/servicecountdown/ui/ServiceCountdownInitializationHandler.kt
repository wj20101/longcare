package com.ytone.longcare.features.servicecountdown.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

private const val NOTIFICATION_PERMISSION_DENIED_HINT =
    "通知权限被拒绝，可能无法收到倒计时完成提醒。请到设置中手动开启通知权限。"

internal suspend fun setupCountdownSessionIfNeeded(
    context: Context,
    orderKey: OrderKey,
    projectIdList: List<Int>,
    sharedViewModel: SharedOrderDetailViewModel,
    countdownViewModel: ServiceCountdownViewModel,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    exactAlarmPermissionLauncher: ActivityResultLauncher<Intent>,
    onPermissionDialogRequired: (String) -> Unit
) {
    val orderInfo = sharedViewModel.getCachedOrderInfo(orderKey) ?: return
    if (!countdownViewModel.shouldReinitialize(projectIdList)) return

    if (countdownViewModel.shouldCheckPermissions()) {
        checkAndRequestCountdownPermissions(
            context = context,
            canScheduleExactAlarms = countdownViewModel.canScheduleExactAlarms(),
            canUseFullScreenIntent = countdownViewModel.canUseFullScreenIntent(),
            notificationPermissionLauncher = notificationPermissionLauncher,
            exactAlarmPermissionLauncher = exactAlarmPermissionLauncher,
            onPermissionDialogRequired = onPermissionDialogRequired
        )
        countdownViewModel.markPermissionsChecked()
    }

    val initialized = countdownViewModel.initializeCountdownSession(
        orderKey = orderKey,
        projectList = orderInfo.projectList ?: emptyList(),
        selectedProjectIds = projectIdList
    )
    if (!initialized) return

    if (!hasNotificationPermission(context)) {
        onPermissionDialogRequired(NOTIFICATION_PERMISSION_DENIED_HINT)
    }

    countdownViewModel.markInitialized(projectIdList)
}
