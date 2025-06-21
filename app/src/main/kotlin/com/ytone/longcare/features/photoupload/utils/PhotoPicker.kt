package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 图片选择器工具类
 * 支持单选和多选，包含完整的降级机制
 */
class PhotoPicker {
    
    companion object {
        /**
         * 检查是否支持现代图片选择器
         */
        fun isPhotoPickerAvailable(): Boolean {
            return ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable()
        }
    }
}

/**
 * 检查URI是否为GIF图片
 */
private fun isGifImage(context: Context, uri: Uri): Boolean {
    return try {
        val mimeType = context.contentResolver.getType(uri)
        mimeType == "image/gif"
    } catch (e: Exception) {
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
 * 创建单张图片选择器
 */
@Composable
fun rememberSinglePhotoPicker(
    filterGif: Boolean = true,
    onGifFiltered: (() -> Unit)? = null,
    onImageSelected: (Uri?) -> Unit
): PhotoPickerLauncher {
    val context = LocalContext.current
    
    return if (PhotoPicker.isPhotoPickerAvailable()) {
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
    
    return if (PhotoPicker.isPhotoPickerAvailable()) {
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