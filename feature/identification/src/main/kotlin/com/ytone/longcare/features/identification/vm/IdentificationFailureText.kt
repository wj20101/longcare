package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.domain.ServicePersonVerificationFailure
import com.ytone.longcare.features.identification.domain.SetupFaceFailure
import com.ytone.longcare.features.identification.domain.UploadElderPhotoFailure

internal fun ResourceTextResolver.resolve(failure: SetupFaceFailure): String = when (failure) {
    SetupFaceFailure.CurrentUserUnavailable -> text(
        R.string.identification_current_user_unavailable,
    )
    is SetupFaceFailure.ImageUpload -> resolveOrFallback(
        failure.detail,
        R.string.identification_face_image_upload_failed,
    )
    is SetupFaceFailure.ServerRejected -> resolveOrFallback(
        failure.message,
        R.string.identification_face_server_update_failed,
    )
    SetupFaceFailure.NetworkError -> text(R.string.identification_network_error)
}

internal fun ResourceTextResolver.resolve(failure: UploadElderPhotoFailure): String = when (failure) {
    is UploadElderPhotoFailure.ImageUpload -> resolveOrFallback(
        failure.detail,
        R.string.identification_elder_photo_upload_failed,
    )
    is UploadElderPhotoFailure.ServerRejected -> resolveOrFallback(
        failure.message,
        R.string.identification_elder_photo_submit_failed,
    )
    UploadElderPhotoFailure.NetworkError -> text(R.string.identification_network_error)
}

internal fun ResourceTextResolver.resolve(failure: ServicePersonVerificationFailure): String =
    when (failure) {
        ServicePersonVerificationFailure.CurrentUserUnavailable -> text(
            R.string.identification_current_user_unavailable,
        )
    }

internal fun ResourceTextResolver.resolve(failure: FaceSetupPreparationFailure): String =
    when (failure) {
        FaceSetupPreparationFailure.IMAGE_FILE_MISSING -> text(
            R.string.identification_face_file_missing,
        )
        FaceSetupPreparationFailure.CURRENT_USER_UNAVAILABLE -> text(
            R.string.identification_current_user_unavailable,
        )
        FaceSetupPreparationFailure.CURRENT_USER_INCOMPLETE -> text(
            R.string.identification_user_info_incomplete,
        )
    }

private fun ResourceTextResolver.resolveOrFallback(
    value: String?,
    fallbackRes: Int,
): String = value?.takeIf(String::isNotBlank) ?: text(fallbackRes)
