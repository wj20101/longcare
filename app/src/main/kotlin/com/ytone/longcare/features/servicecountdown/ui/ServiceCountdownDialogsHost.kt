package com.ytone.longcare.features.servicecountdown.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey

@Composable
internal fun ServiceCountdownDialogsHost(
    showPermissionDialog: Boolean,
    permissionDialogMessage: String,
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
    PermissionAlertDialog(
        visible = showPermissionDialog,
        message = permissionDialogMessage,
        onDismiss = onDismissPermissionDialog,
        onNavigateSettings = {
            context.startActivity(
                buildPermissionSettingsIntent(context, permissionDialogMessage)
            )
        }
    )

    ConfirmEarlyEndServiceDialog(
        visible = showConfirmDialog,
        onDismiss = onDismissConfirmDialog,
        onConfirm = {
            handleEndService(
                context = context,
                orderKey = orderKey,
                projectIdList = projectIdList,
                countdownViewModel = countdownViewModel,
                locationTrackingViewModel = locationTrackingViewModel,
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
                context = context,
                orderKey = orderKey,
                countdownViewModel = countdownViewModel,
                locationTrackingViewModel = locationTrackingViewModel,
                actions = actions
            )
        }
    )
}
