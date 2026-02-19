package com.ytone.longcare.features.photoupload.utils

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest

data class PhotoPickerConfig(
    val maxSelectionCount: Int = 1,
    val onSingleImageSelected: ((Uri?) -> Unit)? = null,
    val onMultipleImagesSelected: ((List<Uri>) -> Unit)? = null
)

sealed class PhotoPickerLauncher {
    data class Modern(
        val launcher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>
    ) : PhotoPickerLauncher()

    data class Legacy(
        val launcher: ManagedActivityResultLauncher<String, Uri?>
    ) : PhotoPickerLauncher()
}

sealed class MultiplePhotoPickerLauncher {
    data class Modern(
        val launcher: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>
    ) : MultiplePhotoPickerLauncher()

    data class Legacy(
        val launcher: ManagedActivityResultLauncher<String, List<Uri>>
    ) : MultiplePhotoPickerLauncher()
}

data class CameraLauncher(
    val launcher: ManagedActivityResultLauncher<Uri, Boolean>,
    val photoUri: Uri
)
