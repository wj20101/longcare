package com.ytone.longcare.features.photoupload.utils

import android.Manifest
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.common.utils.FileProviderHelper
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import java.io.File

/**
 * 检查现代图片选择器（Photo Picker）是否可用。
 * 这是官方推荐的、使用 Context 的最新检查方式。
 * @param context 上下文。
 * @return 如果可用则返回 true，否则返回 false。
 */
private fun isPhotoPickerAvailable(context: Context): Boolean {
    // 在 Android 11 (API 30) 及以上版本，才有可能使用新的图片选择器
    return ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
}

/**
 * 检查URI是否为GIF图片
 */
private fun isGifImage(context: Context, uri: Uri): Boolean {
    return try {
        val mimeType = context.contentResolver.getType(uri)
        mimeType == "image/gif"
    } catch (_: Exception) {
        // 如果无法获取MIME类型，尝试从文件扩展名判断
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        extension?.lowercase() == "gif"
    }
}

/**
 * 过滤GIF图片，返回有效的URI列表和被过滤的GIF数量
 */
private fun filterGifImages(context: Context, uris: List<Uri>): Pair<List<Uri>, List<Uri>> {
    val validUris = mutableListOf<Uri>()
    val gifUris = mutableListOf<Uri>()
    
    uris.forEach { uri ->
        if (isGifImage(context, uri)) {
            gifUris.add(uri)
        } else {
            validUris.add(uri)
        }
    }
    
    return Pair(validUris, gifUris)
}

/**
 * 图片选择器配置
 */
data class PhotoPickerConfig(
    val maxSelectionCount: Int = 1,
    val onSingleImageSelected: ((Uri?) -> Unit)? = null,
    val onMultipleImagesSelected: ((List<Uri>) -> Unit)? = null
)

/**
 * 单张图片选择器启动器封装
 */
sealed class PhotoPickerLauncher {
    data class Modern(
        val launcher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>
    ) : PhotoPickerLauncher()
    
    data class Legacy(
        val launcher: ManagedActivityResultLauncher<String, Uri?>
    ) : PhotoPickerLauncher()
}

/**
 * 多张图片选择器启动器封装
 */
sealed class MultiplePhotoPickerLauncher {
    data class Modern(
        val launcher: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>
    ) : MultiplePhotoPickerLauncher()
    
    data class Legacy(
        val launcher: ManagedActivityResultLauncher<String, List<Uri>>
    ) : MultiplePhotoPickerLauncher()
}

/**
 * 相机拍照启动器封装
 */
data class CameraLauncher(
    val launcher: ManagedActivityResultLauncher<Uri, Boolean>,
    val photoUri: Uri
)

/**
 * 创建单张图片选择器
 */
@Composable
fun rememberSinglePhotoPicker(
    filterGif: Boolean = true,
    onGifFiltered: (() -> Unit)? = null,
    onImageSelected: (Uri?) -> Unit
): PhotoPickerLauncher {
    val context = LocalContext.current
    
    return if (isPhotoPickerAvailable(context)) {
        // 使用现代图片选择器
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri != null && filterGif && isGifImage(context, uri)) {
                    onGifFiltered?.invoke()
                } else {
                    onImageSelected(uri)
                }
            }
        )
        PhotoPickerLauncher.Modern(launcher)
    } else {
        // 降级到传统图片选择器
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                if (uri != null && filterGif && isGifImage(context, uri)) {
                    onGifFiltered?.invoke()
                } else {
                    onImageSelected(uri)
                }
            }
        )
        PhotoPickerLauncher.Legacy(launcher)
    }
}

/**
 * 创建相机拍照启动器
 * 增强版本，包含错误处理和兼容性处理
 */
@Composable
fun rememberCameraLauncher(
    onPhotoTaken: (Uri) -> Unit,
    onError: ((String) -> Unit)? = null
): CameraLauncher {
    val context = LocalContext.current
    
    // 使用FileProviderHelper创建相机拍照Uri
    val photoUri = remember {
        try {
            FileProviderHelper.createCameraPhotoUri(context)
        } catch (e: Exception) {
            onError?.invoke("创建相机文件失败: ${e.message}")
            // 返回一个默认的Uri，避免崩溃
            Uri.EMPTY
        }
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            try {
                if (success && photoUri != Uri.EMPTY) {
                    // 验证文件是否真的存在
                    val file = File(photoUri.path ?: "")
                    if (file.exists() && file.length() > 0) {
                        onPhotoTaken(photoUri)
                    } else {
                        onError?.invoke("拍照文件创建失败或文件为空")
                    }
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

/**
 * 创建多张图片选择器
 */
@Composable
fun rememberMultiplePhotoPicker(
    maxItems: Int = 10,
    filterGif: Boolean = true,
    onGifFiltered: ((List<Uri>) -> Unit)? = null,
    onImagesSelected: (List<Uri>) -> Unit
): MultiplePhotoPickerLauncher {
    val context = LocalContext.current
    
    return if (isPhotoPickerAvailable(context)) {
        // 使用现代图片选择器
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems),
            onResult = { uris ->
                if (filterGif) {
                    val (validUris, gifUris) = filterGifImages(context, uris)
                    if (gifUris.isNotEmpty()) {
                        onGifFiltered?.invoke(gifUris)
                    }
                    onImagesSelected(validUris)
                } else {
                    onImagesSelected(uris)
                }
            }
        )
        MultiplePhotoPickerLauncher.Modern(launcher)
    } else {
        // 降级到传统选择器
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
            onResult = { uris ->
                if (filterGif) {
                    val (validUris, gifUris) = filterGifImages(context, uris)
                    if (gifUris.isNotEmpty()) {
                        onGifFiltered?.invoke(gifUris)
                    }
                    onImagesSelected(validUris)
                } else {
                    onImagesSelected(uris)
                }
            }
        )
        MultiplePhotoPickerLauncher.Legacy(launcher)
    }
}

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
        // 检查相机权限
        if (!UnifiedPermissionHelper.isCameraPermissionGranted(context)) {
            // 如果没有权限且提供了权限申请器，则申请权限
            if (permissionLauncher != null) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                onError?.invoke("需要相机权限才能使用拍照功能")
            }
            return
        }
        
        // 检查硬件支持
        val packageManager = context.packageManager
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
            onError?.invoke("设备不支持相机功能")
            return
        }
        
        // 检查photoUri是否有效
        if (launcher.photoUri == Uri.EMPTY) {
            onError?.invoke("相机文件URI无效，无法启动相机")
            return
        }
        
        // 启动相机
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
        // 检查photoUri是否有效
        if (launcher.photoUri == Uri.EMPTY) {
            onError?.invoke("相机文件URI无效，无法启动相机")
            return
        }
        
        // 启动相机
        launcher.launcher.launch(launcher.photoUri)
    } catch (e: SecurityException) {
        onError?.invoke("相机权限不足: ${e.message}")
    } catch (e: Exception) {
        onError?.invoke("启动相机失败: ${e.message}")
    }
}

/**
 * 创建带权限申请功能的相机启动器
 * 当没有相机权限时会自动申请权限，然后启动相机
 */
@Composable
fun rememberCameraLauncherWithPermission(
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit = {},
    onPermissionDenied: () -> Unit = {}
): Pair<CameraLauncher, ActivityResultLauncher<String>> {
    val context = LocalContext.current
    
    // 创建权限申请启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限获取成功，重新尝试启动相机
            onError("权限已获取，请重新点击拍照按钮")
        } else {
            // 权限被拒绝
            onPermissionDenied()
        }
    }
    
    // 创建相机启动器
    val cameraLauncher = rememberCameraLauncher(
        onPhotoTaken = onPhotoTaken,
        onError = onError
    )
    
    return Pair(cameraLauncher, permissionLauncher)
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