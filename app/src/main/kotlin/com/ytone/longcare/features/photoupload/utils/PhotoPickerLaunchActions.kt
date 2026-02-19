package com.ytone.longcare.features.photoupload.utils

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.ytone.longcare.common.utils.UnifiedPermissionHelper

/**
 * 启动单张图片选择
 */
fun launchSinglePhotoPicker(
    launcher: PhotoPickerLauncher
) {
    when (launcher) {
        is PhotoPickerLauncher.Modern -> {
            launcher.launcher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        is PhotoPickerLauncher.Legacy -> {
            launcher.launcher.launch("image/*")
        }
    }
}

/**
 * 启动相机拍照
 * 增强版本，包含权限检查、设备检查和安全检查
 * 当没有权限时会自动申请权限
 */
fun launchCamera(
    launcher: CameraLauncher,
    context: Context,
    onError: ((String) -> Unit)? = null,
    permissionLauncher: ActivityResultLauncher<String>? = null
) {
    try {
        if (!UnifiedPermissionHelper.isCameraPermissionGranted(context)) {
            if (permissionLauncher != null) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                onError?.invoke("需要相机权限才能使用拍照功能")
            }
            return
        }

        val packageManager = context.packageManager
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
            onError?.invoke("设备不支持相机功能")
            return
        }

        if (launcher.photoUri == Uri.EMPTY) {
            onError?.invoke("相机文件URI无效，无法启动相机")
            return
        }

        launcher.launcher.launch(launcher.photoUri)
    } catch (e: SecurityException) {
        onError?.invoke("相机权限不足: ${e.message}")
    } catch (e: Exception) {
        onError?.invoke("启动相机失败: ${e.message}")
    }
}

/**
 * 启动相机拍照（重载版本，兼容旧代码）
 * @deprecated 建议使用带context参数的版本以获得更好的错误处理
 */
fun launchCamera(
    launcher: CameraLauncher,
    onError: ((String) -> Unit)? = null
) {
    try {
        if (launcher.photoUri == Uri.EMPTY) {
            onError?.invoke("相机文件URI无效，无法启动相机")
            return
        }

        launcher.launcher.launch(launcher.photoUri)
    } catch (e: SecurityException) {
        onError?.invoke("相机权限不足: ${e.message}")
    } catch (e: Exception) {
        onError?.invoke("启动相机失败: ${e.message}")
    }
}

/**
 * 便捷函数：启动带权限申请的相机拍照
 */
fun launchCameraWithPermission(
    cameraLauncher: CameraLauncher,
    permissionLauncher: ActivityResultLauncher<String>,
    context: Context,
    onError: ((String) -> Unit)? = null
) {
    launchCamera(
        launcher = cameraLauncher,
        context = context,
        onError = onError,
        permissionLauncher = permissionLauncher
    )
}

/**
 * 启动多张图片选择
 */
fun launchMultiplePhotoPicker(
    launcher: MultiplePhotoPickerLauncher
) {
    when (launcher) {
        is MultiplePhotoPickerLauncher.Modern -> {
            launcher.launcher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        is MultiplePhotoPickerLauncher.Legacy -> {
            launcher.launcher.launch("image/*")
        }
    }
}
