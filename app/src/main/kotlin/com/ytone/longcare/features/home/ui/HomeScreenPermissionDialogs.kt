package com.ytone.longcare.features.home.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun HomeScreenPermissionDialogs(
    showPermissionDialog: Boolean,
    permissionDeniedMessage: String,
    onDismissPermissionDialog: () -> Unit,
    onRetryPermissionRequest: () -> Unit,
    showPopupPermissionDialog: Boolean,
    popupPermissionMessage: String,
    onDismissPopupPermissionDialog: () -> Unit,
    onOpenPopupSettings: () -> Unit,
    onSkipPopupGuide: () -> Unit,
    showBatteryDialog: Boolean,
    batteryDialogTitle: String,
    batteryMessage: String,
    onDismissBatteryDialog: () -> Unit,
    onConfirmBatteryGuide: () -> Unit,
    batteryConfirmLabel: String,
    onAcknowledgeBatteryGuide: () -> Unit
) {
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = onDismissPermissionDialog,
            title = { Text("权限请求") },
            text = { Text(permissionDeniedMessage) },
            confirmButton = {
                TextButton(onClick = onRetryPermissionRequest) {
                    Text("重新授权")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPermissionDialog) {
                    Text("稍后再说")
                }
            }
        )
    }

    if (showPopupPermissionDialog) {
        AlertDialog(
            onDismissRequest = onDismissPopupPermissionDialog,
            title = { Text("开启弹窗权限") },
            text = { Text(popupPermissionMessage) },
            confirmButton = {
                TextButton(onClick = onOpenPopupSettings) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = onSkipPopupGuide) {
                    Text("跳过")
                }
            }
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = onDismissBatteryDialog,
            title = { Text(batteryDialogTitle) },
            text = { Text(batteryMessage) },
            confirmButton = {
                TextButton(onClick = onConfirmBatteryGuide) {
                    Text(batteryConfirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = onAcknowledgeBatteryGuide) {
                    Text("我知道了")
                }
            }
        )
    }
}
