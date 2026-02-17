package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        title = { Text("权限提示") },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = singleClick {
                    onDismiss()
                    onNavigateSettings()
                }
            ) {
                Text("去设置")
            }
        },
        dismissButton = {
            TextButton(
                onClick = singleClick { onDismiss() }
            ) {
                Text("稍后")
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
        title = { Text("确认提前结束服务") },
        text = { Text("服务时间尚未结束，确定要提前结束服务吗？") },
        confirmButton = {
            TextButton(
                onClick = singleClick {
                    onDismiss()
                    onConfirm()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = singleClick { onDismiss() }
            ) {
                Text("取消")
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
        title = { Text("订单状态异常") },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = singleClick { onConfirm() }
            ) {
                Text("确定")
            }
        }
    )
}
