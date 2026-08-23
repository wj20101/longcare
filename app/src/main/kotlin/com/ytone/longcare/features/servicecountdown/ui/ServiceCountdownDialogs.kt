package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.singleClick

@Composable
internal fun PermissionAlertDialog(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.service_countdown_permission_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = singleClick {
                    onDismiss()
                    onNavigateSettings()
                }
            ) {
                Text(stringResource(R.string.common_go_to_settings))
            }
        },
        dismissButton = {
            TextButton(
                onClick = singleClick { onDismiss() }
            ) {
                Text(stringResource(R.string.common_later))
            }
        }
    )
}

@Composable
internal fun ConfirmEarlyEndServiceDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.service_countdown_end_early_title)) },
        text = { Text(stringResource(R.string.service_countdown_end_early_message)) },
        confirmButton = {
            TextButton(
                onClick = singleClick {
                    onDismiss()
                    onConfirm()
                }
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = singleClick { onDismiss() }
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun OrderStateErrorDialog(
    visible: Boolean,
    message: String,
    onConfirm: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        title = { Text(stringResource(R.string.service_countdown_order_state_error)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = singleClick { onConfirm() }
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        }
    )
}
