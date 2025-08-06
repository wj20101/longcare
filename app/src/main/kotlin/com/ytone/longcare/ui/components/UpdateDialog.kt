package com.ytone.longcare.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R
import com.ytone.longcare.api.response.AppVersionModel

@Composable
fun UpdateDialog(
    appVersionModel: AppVersionModel,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val isForceUpdate = appVersionModel.upType == 1

    AlertDialog(
        onDismissRequest = {
            if (!isForceUpdate) {
                onDismiss()
            }
        },
        title = { Text(text = stringResource(id = R.string.new_version_found)) },
        text = { Text(text = appVersionModel.remarks) },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(id = R.string.update_now))
            }
        },
        dismissButton = {
            if (!isForceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.update_later))
                }
            }
        }
    )
}