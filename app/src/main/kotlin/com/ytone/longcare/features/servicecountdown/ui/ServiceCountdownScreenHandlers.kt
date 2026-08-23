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
    onPermissionDialogRequired: (ServiceCountdownPermissionIssue) -> Unit
): ServiceCountdownScreenLaunchers {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            onPermissionDialogRequired(ServiceCountdownPermissionIssue.NOTIFICATION)
        }
    }

    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (!countdownViewModel.canScheduleExactAlarms()) {
            onPermissionDialogRequired(ServiceCountdownPermissionIssue.EXACT_ALARM)
        }
    }

    val locationPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = { locationTrackingViewModel.startTrackingAfterPermissionGrant(orderKey) }
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
    photoRequiredMessage: String,
    onShowToast: (String) -> Unit,
    onRequireConfirm: () -> Unit,
    onEndServiceDirectly: () -> Unit
) {
    if (!validatePhotosUploaded()) {
        onShowToast(photoRequiredMessage)
        return
    }

    if (countdownState == ServiceCountdownState.RUNNING) {
        onRequireConfirm()
    } else {
        onEndServiceDirectly()
    }
}
