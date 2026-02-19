package com.ytone.longcare.features.photoupload.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.common.utils.FileProviderHelper

@Composable
fun rememberCameraLauncher(
    onPhotoTaken: (Uri) -> Unit,
    onError: ((String) -> Unit)? = null
): CameraLauncher {
    val context = LocalContext.current
    val photoUri = remember {
        try {
            FileProviderHelper.createCameraPhotoUri(context)
        } catch (e: Exception) {
            onError?.invoke("创建相机文件失败: ${e.message}")
            Uri.EMPTY
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            try {
                if (success && photoUri != Uri.EMPTY) {
                    onPhotoTaken(photoUri)
                } else {
                    onError?.invoke("拍照被取消或失败")
                }
            } catch (e: Exception) {
                onError?.invoke("处理拍照结果时出错: ${e.message}")
            }
        }
    )

    return CameraLauncher(launcher, photoUri)
}

@Composable
fun rememberCameraLauncherWithPermission(
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit = {},
    onPermissionDenied: () -> Unit = {}
): Pair<CameraLauncher, ActivityResultLauncher<String>> {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onError("权限已获取，请重新点击拍照按钮")
        } else {
            onPermissionDenied()
        }
    }

    val cameraLauncher = rememberCameraLauncher(
        onPhotoTaken = onPhotoTaken,
        onError = onError
    )

    return Pair(cameraLauncher, permissionLauncher)
}
