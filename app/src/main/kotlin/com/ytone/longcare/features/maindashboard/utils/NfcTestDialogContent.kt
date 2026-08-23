package com.ytone.longcare.features.maindashboard.utils

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R

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
            Text(text = stringResource(R.string.nfc_tag_detected))
        },
        text = {
            Text(text = stringResource(R.string.nfc_tag_id, nfcTagId))
        },
        confirmButton = {
            Button(onClick = onCopy) {
                Text(stringResource(R.string.common_copy))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
