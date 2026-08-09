package com.ytone.longcare.features.servicecountdown.ui

import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey

internal data class ServiceCountdownScreenLaunchers(
    val notificationPermissionLauncher: ActivityResultLauncher<String>,
    val exactAlarmPermissionLauncher: ActivityResultLauncher<Intent>,
    val locationPermissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>
)

@Composable
internal fun rememberServiceCountdownScreenLaunchers(
    countdownViewModel: ServiceCountdownViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    orderKey: OrderKey,
    onPermissionDialogRequired: (String) -> Unit
): ServiceCountdownScreenLaunchers {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            onPermissionDialogRequired("通知权限被拒绝，可能无法收到倒计时完成提醒。请到设置中手动开启通知权限。")
        }
    }

    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (!countdownViewModel.canScheduleExactAlarms()) {
            onPermissionDialogRequired("精确闹钟权限被拒绝，可能无法准时收到倒计时完成提醒。请到设置中手动开启精确闹钟权限。")
        }
    }

    val locationPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = { locationTrackingViewModel.onPermissionGrantedAndStartTracking(orderKey) }
    )

    return ServiceCountdownScreenLaunchers(
        notificationPermissionLauncher = notificationPermissionLauncher,
        exactAlarmPermissionLauncher = exactAlarmPermissionLauncher,
        locationPermissionLauncher = locationPermissionLauncher
    )
}

internal fun handleServiceCountdownBottomAction(
    countdownState: ServiceCountdownState,
    validatePhotosUploaded: () -> Boolean,
    onShowToast: (String) -> Unit,
    onRequireConfirm: () -> Unit,
    onEndServiceDirectly: () -> Unit
) {
    if (!validatePhotosUploaded()) {
        onShowToast("请上传照片")
        return
    }

    if (countdownState == ServiceCountdownState.RUNNING) {
        onRequireConfirm()
    } else {
        onEndServiceDirectly()
    }
}
