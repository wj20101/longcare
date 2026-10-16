package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ytone.longcare.R
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.core.ui.R as CoreUiR

internal enum class ServiceCountdownPermissionIssue(
    @param:StringRes val messageRes: Int,
) {
    LOCATION(
        R.string.service_countdown_location_permission_required,
    ),
    LOCATION_SERVICE(
        CoreUiR.string.location_service_open_settings,
    ),
    NOTIFICATION(
        R.string.service_countdown_notification_permission_required,
    ),
    CHANNEL(
        R.string.service_countdown_notification_channel_required,
    ),
    EXACT_ALARM(
        R.string.service_countdown_exact_alarm_permission_required,
    ),
    FULL_SCREEN(
        R.string.service_countdown_full_screen_permission_required,
    ),
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

/** 每次授权或设置返回后继续检查下一项，避免只检查第一项便永久跳过全屏提醒。 */
internal fun requiredCountdownPermissionIssue(
    context: Context,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
): ServiceCountdownPermissionIssue? {
    val notifications = NotificationManagerCompat.from(context)
    val channel = notifications.getNotificationChannelCompat(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID)
    return when {
        !UnifiedPermissionHelper.hasLocationPermission(context) -> ServiceCountdownPermissionIssue.LOCATION
        !UnifiedPermissionHelper.isLocationServiceEnabled(context) -> ServiceCountdownPermissionIssue.LOCATION_SERVICE
        !hasNotificationPermission(context) || !notifications.areNotificationsEnabled() ->
            ServiceCountdownPermissionIssue.NOTIFICATION
        (channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) ||
            isCountdownNotificationGroupBlocked(notifications) ->
            ServiceCountdownPermissionIssue.CHANNEL
        !canScheduleExactAlarms -> ServiceCountdownPermissionIssue.EXACT_ALARM
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreenIntent ->
            ServiceCountdownPermissionIssue.FULL_SCREEN
        else -> null
    }
}

internal fun buildPermissionSettingsIntent(
    context: Context,
    issue: ServiceCountdownPermissionIssue,
): Intent {
    if (issue == ServiceCountdownPermissionIssue.LOCATION_SERVICE) {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
    val intent = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        issue == ServiceCountdownPermissionIssue.FULL_SCREEN
    ) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        issue == ServiceCountdownPermissionIssue.EXACT_ALARM) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        issue == ServiceCountdownPermissionIssue.CHANNEL &&
        !isCountdownNotificationGroupBlocked(NotificationManagerCompat.from(context))) {
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        (issue == ServiceCountdownPermissionIssue.NOTIFICATION || issue == ServiceCountdownPermissionIssue.CHANNEL)) {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }
    return intent.apply { data = "package:${context.packageName}".toUri() }
}

/** A blocked group prevents its channels from notifying even when channel importance is high. */
private fun isCountdownNotificationGroupBlocked(notifications: NotificationManagerCompat): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val group = notifications.getNotificationChannelCompat(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID)
        ?.group ?: return false
    return notifications.getNotificationChannelGroupCompat(group)?.isBlocked == true
}
