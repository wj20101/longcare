package com.ytone.longcare.features.identification.vm

import android.net.Uri
import com.ytone.longcare.features.identification.domain.UploadElderPhotoResult
import com.ytone.longcare.features.identification.domain.UploadElderPhotoUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
