package com.ytone.longcare.features.maindashboard.utils

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun NfcTagDialogContent(
    nfcTagId: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // 点击空白处不可取消，所以这里不做任何操作
        },
        title = {
            Text(text = "检测到NFC标签")
        },
        text = {
            Text(text = "Tag ID: $nfcTagId")
        },
        confirmButton = {
            Button(onClick = onCopy) {
                Text("复制")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
