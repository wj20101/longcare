package com.ytone.longcare.features.servicecountdown.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.logW
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey

internal data class ServiceCountdownScreenLaunchers(
    val checkPermissions: () -> Unit,
    val requestPermission: (ServiceCountdownPermissionIssue) -> Unit,
    val openPermissionSettings: (ServiceCountdownPermissionIssue) -> Unit,
)

@Composable
internal fun rememberServiceCountdownScreenLaunchers(
    orderKey: OrderKey,
    canScheduleExactAlarms: () -> Boolean,
    canUseFullScreenIntent: () -> Boolean,
    onLocationPermissionGranted: () -> Unit,
    onPermissionDialogRequired: (ServiceCountdownPermissionIssue?) -> Unit
): ServiceCountdownScreenLaunchers {
    val context = LocalContext.current
    var pendingIssue by rememberSaveable(orderKey) {
        mutableStateOf<ServiceCountdownPermissionIssue?>(null)
    }
    val currentIssue = {
        requiredCountdownPermissionIssue(context, canScheduleExactAlarms(), canUseFullScreenIntent())
    }
    val continuePermissionFlow = {
        if (UnifiedPermissionHelper.hasLocationPermission(context) &&
            UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
            onLocationPermissionGranted()
        }
        val requestedIssue = pendingIssue
        val remainingIssue = currentIssue()
        pendingIssue = null
        if (requestedIssue != null && requestedIssue == remainingIssue) {
            // Returning without granting access is a decision, not another invitation to Settings.
            onPermissionDialogRequired(null)
            Toast.makeText(context, R.string.service_countdown_permission_unchanged, Toast.LENGTH_SHORT).show()
        } else {
            onPermissionDialogRequired(remainingIssue)
        }
        Unit
    }
    val permissionSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        continuePermissionFlow()
    }

    // Location and notifications have different purposes; request only the item the user chose.
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { continuePermissionFlow() }
    val openSettings: (ServiceCountdownPermissionIssue) -> Unit = { issue ->
        if (pendingIssue == null) {
            pendingIssue = issue
            openCountdownPermissionSettings(
                context = context,
                issue = issue,
                launch = { permissionSettingsLauncher.launch(it) },
                onUnavailable = {
                    pendingIssue = null
                    Toast.makeText(context, R.string.service_countdown_settings_unavailable, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    return ServiceCountdownScreenLaunchers(
        checkPermissions = {
            onPermissionDialogRequired(currentIssue())
        },
        requestPermission = { issue ->
            val actualIssue = currentIssue()
            if (actualIssue != issue) {
                onPermissionDialogRequired(actualIssue)
            } else if (pendingIssue == null) {
                when {
                    issue == ServiceCountdownPermissionIssue.LOCATION -> {
                        pendingIssue = issue
                        runtimePermissionLauncher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
                    }
                    issue == ServiceCountdownPermissionIssue.NOTIFICATION &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context) -> {
                        pendingIssue = issue
                        runtimePermissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                    }
                    else -> openSettings(issue)
                }
            }
        },
        openPermissionSettings = openSettings,
    )
}

/** 部分 ROM 缺少标准权限页；复用同一结果回调，退回本应用详情继续人工设置。 */
internal fun openCountdownPermissionSettings(
    context: Context,
    issue: ServiceCountdownPermissionIssue,
    launch: (Intent) -> Unit,
    onUnavailable: () -> Unit,
) {
    val preferred = buildPermissionSettingsIntent(context, issue)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri())
    val candidates = if (preferred.filterEquals(appDetails)) listOf(preferred) else listOf(preferred, appDetails)
    for (intent in candidates) {
        try {
            launch(intent)
            return
        } catch (error: ActivityNotFoundException) {
            context.logW("系统未提供设置页：${intent.action}，继续尝试可用入口", throwable = error)
        } catch (error: SecurityException) {
            context.logW("系统拒绝打开设置页：${intent.action}，继续尝试可用入口", throwable = error)
        }
    }
    onUnavailable()
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
