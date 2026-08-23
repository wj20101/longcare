package com.ytone.longcare.features.servicecountdown.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey
import androidx.compose.ui.res.stringResource

@Composable
internal fun ServiceCountdownDialogsHost(
    showPermissionDialog: Boolean,
    permissionIssue: ServiceCountdownPermissionIssue?,
    onDismissPermissionDialog: () -> Unit,
    showConfirmDialog: Boolean,
    onDismissConfirmDialog: () -> Unit,
    showOrderStateErrorDialog: Boolean,
    orderStateErrorMessage: String,
    onDismissOrderStateErrorDialog: () -> Unit,
    context: Context,
    orderKey: OrderKey,
    projectIdList: List<Int>,
    countdownViewModel: ServiceCountdownViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    actions: ServiceCountdownActions
) {
    val permissionDialogMessage = permissionIssue?.let { stringResource(it.messageRes) }.orEmpty()
    PermissionAlertDialog(
        visible = showPermissionDialog && permissionIssue != null,
        message = permissionDialogMessage,
        onDismiss = onDismissPermissionDialog,
        onNavigateSettings = {
            context.startActivity(
                buildPermissionSettingsIntent(context, requireNotNull(permissionIssue))
            )
        }
    )

    ConfirmEarlyEndServiceDialog(
        visible = showConfirmDialog,
        onDismiss = onDismissConfirmDialog,
        onConfirm = {
            handleEndService(
                orderKey = orderKey,
                projectIdList = projectIdList,
                countdownViewModel = countdownViewModel,
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
                locationTrackingViewModel = locationTrackingViewModel,
                actions = actions
            )
        }
    )
}
