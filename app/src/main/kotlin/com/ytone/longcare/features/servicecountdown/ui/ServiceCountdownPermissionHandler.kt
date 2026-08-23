package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResultLauncher
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.annotation.StringRes
import com.ytone.longcare.R

internal enum class ServiceCountdownPermissionIssue(
    @param:StringRes val messageRes: Int,
    val settingsDestination: SettingsDestination,
) {
    NOTIFICATION(
        R.string.service_countdown_notification_permission_denied,
        SettingsDestination.APP_DETAILS,
    ),
    EXACT_ALARM(
        R.string.service_countdown_exact_alarm_permission_denied,
        SettingsDestination.APP_DETAILS,
    ),
    FULL_SCREEN(
        R.string.service_countdown_full_screen_permission_required,
        SettingsDestination.FULL_SCREEN_NOTIFICATION,
    ),
}

internal enum class SettingsDestination {
    APP_DETAILS,
    FULL_SCREEN_NOTIFICATION,
}

internal fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

internal fun checkAndRequestRequiredPermissions(
    context: Context,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    requestNotificationPermission: () -> Unit,
    requestExactAlarmPermission: () -> Unit,
    onPermissionDialogRequired: (ServiceCountdownPermissionIssue) -> Unit
) {
    if (!hasNotificationPermission(context)) {
        requestNotificationPermission()
        return
    }
    if (!canScheduleExactAlarms) {
        requestExactAlarmPermission()
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreenIntent) {
        onPermissionDialogRequired(ServiceCountdownPermissionIssue.FULL_SCREEN)
    }
}

internal fun checkAndRequestCountdownPermissions(
    context: Context,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    exactAlarmPermissionLauncher: ActivityResultLauncher<Intent>,
    onPermissionDialogRequired: (ServiceCountdownPermissionIssue) -> Unit
) {
    checkAndRequestRequiredPermissions(
        context = context,
        canScheduleExactAlarms = canScheduleExactAlarms,
        canUseFullScreenIntent = canUseFullScreenIntent,
        requestNotificationPermission = {
            requestNotificationPermission(
                context = context,
                notificationPermissionLauncher = notificationPermissionLauncher
            )
        },
        requestExactAlarmPermission = {
            requestExactAlarmPermission(
                context = context,
                canScheduleExactAlarms = canScheduleExactAlarms,
                exactAlarmPermissionLauncher = exactAlarmPermissionLauncher
            )
        },
        onPermissionDialogRequired = onPermissionDialogRequired
    )
}

internal fun requestNotificationPermission(
    context: Context,
    notificationPermissionLauncher: ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

internal fun requestExactAlarmPermission(
    context: Context,
    canScheduleExactAlarms: Boolean,
    exactAlarmPermissionLauncher: ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
        }
        exactAlarmPermissionLauncher.launch(intent)
    }
}

internal fun checkLocationPermissionAndStart(
    context: Context,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onPermissionGranted: () -> Unit
) {
    UnifiedPermissionHelper.checkLocationPermissionAndStart(
        context = context,
        permissionLauncher = permissionLauncher,
        onPermissionGranted = onPermissionGranted
    )
}

internal fun buildPermissionSettingsIntent(
    context: Context,
    issue: ServiceCountdownPermissionIssue,
): Intent {
    return if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        issue.settingsDestination == SettingsDestination.FULL_SCREEN_NOTIFICATION
    ) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = "package:${context.packageName}".toUri()
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }
}
