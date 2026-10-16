package com.ytone.longcare.features.servicecountdown.ui

import com.ytone.longcare.core.ui.R as CoreUiR

import androidx.compose.material3.AlertDialog
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
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
    @StringRes confirmLabelRes: Int,
    onConfirm: () -> Unit,
    onOpenAppSettings: (() -> Unit)? = null,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.service_countdown_permission_title)) },
        text = {
            Column {
                Text(message)
                if (onOpenAppSettings != null) {
                    TextButton(onClick = singleClick {
                        onDismiss()
                        onOpenAppSettings()
                    }) {
                        Text(stringResource(R.string.common_go_to_settings))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = singleClick {
                    onDismiss()
                    onConfirm()
                }
            ) {
                Text(stringResource(confirmLabelRes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = singleClick { onDismiss() }
            ) {
                Text(stringResource(R.string.service_countdown_permission_later))
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
                Text(stringResource(CoreUiR.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = singleClick { onDismiss() }
            ) {
                Text(stringResource(CoreUiR.string.common_cancel))
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
                Text(stringResource(CoreUiR.string.common_confirm))
            }
        }
    )
}
