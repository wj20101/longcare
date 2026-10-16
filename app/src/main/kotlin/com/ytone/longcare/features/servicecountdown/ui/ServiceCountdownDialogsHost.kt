package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.runtime.Composable
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.R

@Composable
internal fun ServiceCountdownDialogsHost(
    permissionIssue: ServiceCountdownPermissionIssue?,
    onDismissPermissionDialog: () -> Unit,
    showConfirmDialog: Boolean,
    onDismissConfirmDialog: () -> Unit,
    showOrderStateErrorDialog: Boolean,
    orderStateErrorMessage: String,
    onDismissOrderStateErrorDialog: () -> Unit,
    onOpenPermissionSettings: (ServiceCountdownPermissionIssue) -> Unit,
    onRequestPermission: (ServiceCountdownPermissionIssue) -> Unit,
    orderKey: OrderKey,
    projectIdList: List<Int>,
    countdownViewModel: ServiceCountdownViewModel,
    actions: ServiceCountdownActions
) {
    val permissionDialogMessage = permissionIssue?.let { stringResource(it.messageRes) }.orEmpty()
    val runtimeRequest = permissionIssue == ServiceCountdownPermissionIssue.LOCATION ||
        (permissionIssue == ServiceCountdownPermissionIssue.NOTIFICATION && !hasNotificationPermission(LocalContext.current))
    PermissionAlertDialog(
        visible = permissionIssue != null,
        message = permissionDialogMessage,
        onDismiss = onDismissPermissionDialog,
        confirmLabelRes = when {
            permissionIssue == ServiceCountdownPermissionIssue.LOCATION -> R.string.service_countdown_allow_location
            runtimeRequest -> R.string.service_countdown_allow_notifications
            else -> R.string.common_go_to_settings
        },
        onConfirm = { permissionIssue?.let(onRequestPermission) },
        onOpenAppSettings = if (runtimeRequest) {
            { permissionIssue?.let(onOpenPermissionSettings) }
        } else null,
    )

    ConfirmEarlyEndServiceDialog(
        visible = showConfirmDialog,
        onDismiss = onDismissConfirmDialog,
        onConfirm = {
            handleEndService(
                orderKey = orderKey,
                projectIdList = projectIdList,
                actions = actions,
                endType = 2
            )
        }
    )

    OrderStateErrorDialog(
        visible = showOrderStateErrorDialog,
        message = orderStateErrorMessage,
        onConfirm = {
            onDismissOrderStateErrorDialog()
            handleOrderStateErrorAndExit(
                orderKey = orderKey,
                countdownViewModel = countdownViewModel,
                actions = actions
            )
        }
    )
}
