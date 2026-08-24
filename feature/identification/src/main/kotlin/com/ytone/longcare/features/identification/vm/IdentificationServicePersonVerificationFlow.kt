package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import com.ytone.longcare.feature.identification.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchSelfProvidedFaceVerificationWithRemoteSource(
    scope: CoroutineScope,
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    sourceUserId: Int,
    sourcePhotoUrl: String,
    faceDataSource: IdentificationFaceDataSource,
    beginVerification: (VerificationType) -> Unit,
    startVerificationWithRequest: suspend (FaceVerificationRequest) -> Unit,
    onFailure: (String) -> Unit,
    textResolver: ResourceTextResolver,
) {
    scope.launch {
        beginVerification(VerificationType.SERVICE_PERSON)

        try {
            val sourcePhotoBase64 = faceDataSource.downloadAndConvertToBase64(
                url = sourcePhotoUrl,
                userId = sourceUserId,
            )
            executeSelfProvidedVerification(
                name = name,
                idNo = idNo,
                orderNo = orderNo,
                userId = userId,
                sourcePhotoBase64 = sourcePhotoBase64,
                startVerificationWithRequest = startVerificationWithRequest,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("下载人脸图片失败", tag = "IdentificationVM", throwable = e)
            FaceVerificationEventTracker.trackError(
                eventType = EventType.REMOTE_FACE_DOWNLOAD_ERROR,
                throwable = e,
                extras = FaceVerificationEventTracker.safeUrlExtras(sourcePhotoUrl) + mapOf(
                    "userId" to sourceUserId,
                    "orderNo" to orderNo,
                ),
            )
            onFailure(
                textResolver.text(
                    R.string.identification_face_download_failed,
                    textResolver.text(R.string.identification_retry_later),
                ),
            )
        }
    }
}

private suspend fun executeSelfProvidedVerification(
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    sourcePhotoBase64: String,
    startVerificationWithRequest: suspend (FaceVerificationRequest) -> Unit,
) {
    val request = createFaceVerificationRequest(
        name = name,
        idNo = idNo,
        orderNo = orderNo,
        userId = userId,
        sourcePhotoBase64 = sourcePhotoBase64,
    )
    startVerificationWithRequest(request)
}
