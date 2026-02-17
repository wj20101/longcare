package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

private const val FULL_SCREEN_PERMISSION_HINT = """
    为了在服务时间结束时能准时提醒您，需要开启「全屏通知」权限。
    
    请在设置中找到「全屏通知」或「显示在其他应用上层」选项并开启。
"""

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
    onPermissionDialogRequired: (String) -> Unit
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
        onPermissionDialogRequired(FULL_SCREEN_PERMISSION_HINT.trimIndent())
    }
}

internal fun buildPermissionSettingsIntent(
    context: Context,
    permissionDialogMessage: String
): Intent {
    return if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        permissionDialogMessage.contains("全屏通知")
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
