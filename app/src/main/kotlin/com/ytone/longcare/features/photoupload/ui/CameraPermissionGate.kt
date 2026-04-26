package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.cameraPermissionPurposeNotice

@Composable
internal fun CameraPermissionGate(
    onPermissionGranted: @Composable () -> Unit,
) {
    var hasPermission by remember { mutableStateOf(false) }
    var showPurposeNotice by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
        }
    )

    if (hasPermission) {
        onPermissionGranted()
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Button(onClick = { showPurposeNotice = true }) {
                Text("申请相机权限")
            }
        }
    }

    if (showPurposeNotice) {
        PermissionPurposeDialog(
            notice = cameraPermissionPurposeNotice("拍摄服务照片"),
            onConfirm = {
                showPurposeNotice = false
                launcher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showPurposeNotice = false }
        )
    }
}
