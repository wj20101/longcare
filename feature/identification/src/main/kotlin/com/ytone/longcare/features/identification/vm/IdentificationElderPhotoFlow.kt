package com.ytone.longcare.features.identification.vm

import android.net.Uri
import com.ytone.longcare.features.identification.domain.UploadElderPhotoResult
import com.ytone.longcare.features.identification.domain.UploadElderPhotoUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal fun launchElderPhotoUpload(
    scope: CoroutineScope,
    uploadElderPhotoUseCase: UploadElderPhotoUseCase,
    photoUri: Uri,
    orderId: Long,
    onProcessing: () -> Unit,
    onUploading: () -> Unit,
    onUploadSuccess: () -> Unit,
    onUploadError: (String) -> Unit,
    onUnexpectedError: (String?) -> Unit,
) {
    scope.launch {
        try {
            onProcessing()
            onUploading()
            when (val result = uploadElderPhotoUseCase.execute(photoUri, orderId)) {
                UploadElderPhotoResult.Success -> onUploadSuccess()
                is UploadElderPhotoResult.Error -> onUploadError(result.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onUnexpectedError(e.message)
        }
    }
}

internal fun launchElderPhotoUploadWithBindings(
    scope: CoroutineScope,
    uploadElderPhotoUseCase: UploadElderPhotoUseCase,
    photoUri: Uri,
    orderId: Long,
    photoUploadState: MutableStateFlow<PhotoUploadState>,
    showToast: (String) -> Unit,
    onElderVerified: () -> Unit,
    onSuccess: () -> Unit,
) {
    launchElderPhotoUpload(
        scope = scope,
        uploadElderPhotoUseCase = uploadElderPhotoUseCase,
        photoUri = photoUri,
        orderId = orderId,
        onProcessing = { photoUploadState.value = PhotoUploadState.Processing },
        onUploading = { photoUploadState.value = PhotoUploadState.Uploading },
        onUploadSuccess = {
            photoUploadState.value = PhotoUploadState.Success
            showToast("老人照片上传成功")
            onElderVerified()
            onSuccess()
        },
        onUploadError = { message ->
            photoUploadState.value = PhotoUploadState.Error(message)
            showToast(message)
        },
        onUnexpectedError = { message ->
            photoUploadState.value = PhotoUploadState.Error(message ?: "未知错误")
            showToast("处理失败: $message")
        }
    )
}
