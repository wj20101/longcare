package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.common.utils.FileProviderHelper

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
 */
@Composable
fun rememberCameraLauncher(
    onPhotoTaken: (Uri) -> Unit
): CameraLauncher {
    val context = LocalContext.current
    
    // 使用FileProviderHelper创建相机拍照Uri
    val photoUri = remember {
        FileProviderHelper.createCameraPhotoUri(context)
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                onPhotoTaken(photoUri)
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
 */
fun launchCamera(
    launcher: CameraLauncher
) {
    launcher.launcher.launch(launcher.photoUri)
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