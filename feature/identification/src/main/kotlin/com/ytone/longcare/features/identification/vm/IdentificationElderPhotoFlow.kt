package com.ytone.longcare.features.identification.vm

import android.net.Uri
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.domain.UploadElderPhotoResult
import com.ytone.longcare.features.identification.domain.UploadElderPhotoFailure
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
    onUploadError: (UploadElderPhotoFailure) -> Unit,
    onUnexpectedError: () -> Unit,
) {
    scope.launch {
        try {
            onProcessing()
            onUploading()
            when (val result = uploadElderPhotoUseCase.execute(photoUri, orderId)) {
                UploadElderPhotoResult.Success -> onUploadSuccess()
                is UploadElderPhotoResult.Error -> {
                    DiagnosticEventTracker.trackError(
                        category = IDENTIFICATION_PHOTO_DIAGNOSTIC_CATEGORY,
                        event = "elder_photo_upload_failure",
                        description = "身份认证老人照片上传失败",
                        extras = photoUri.diagnosticExtras(orderId) + mapOf(
                            "failureType" to result.failure::class.simpleName,
                        ),
                    )
                    onUploadError(result.failure)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticEventTracker.trackError(
                category = IDENTIFICATION_PHOTO_DIAGNOSTIC_CATEGORY,
                event = "elder_photo_upload_exception",
                description = "身份认证老人照片上传异常",
                throwable = e,
                extras = photoUri.diagnosticExtras(orderId),
            )
            onUnexpectedError()
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
    textResolver: ResourceTextResolver,
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
            showToast(textResolver.text(R.string.identification_elder_photo_upload_succeeded))
            onElderVerified()
            onSuccess()
        },
        onUploadError = { failure ->
            val message = textResolver.resolve(failure)
            photoUploadState.value = PhotoUploadState.Error(message)
            showToast(message)
        },
        onUnexpectedError = {
            val displayMessage = textResolver.text(R.string.identification_retry_later)
            photoUploadState.value = PhotoUploadState.Error(displayMessage)
            showToast(
                textResolver.text(
                    R.string.identification_elder_photo_processing_failed,
                    displayMessage,
                ),
            )
        }
    )
}

private fun Uri.diagnosticExtras(orderId: Long): Map<String, Any?> =
    mapOf(
        "orderId" to orderId,
        "uriScheme" to scheme,
        "uriPathLength" to (path?.length ?: 0),
    )

private const val IDENTIFICATION_PHOTO_DIAGNOSTIC_CATEGORY = "identification_photo"
