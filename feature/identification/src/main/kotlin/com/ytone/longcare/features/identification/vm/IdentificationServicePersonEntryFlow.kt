package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.domain.ServicePersonProfile
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import com.ytone.longcare.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchServicePersonVerification(
    scope: CoroutineScope,
    context: Context,
    resolveCurrentUser: suspend () -> User?,
    verifyServicePersonUseCase: VerifyServicePersonUseCase,
    createOrderNo: () -> String,
    faceDataSource: IdentificationFaceDataSource,
    beginVerification: (VerificationType) -> Unit,
    startVerificationWithRequest: suspend (Context, FaceVerificationRequest) -> Unit,
    onRequireFaceSetup: () -> Unit,
    onVerificationFailure: (String, Throwable?) -> Unit,
) {
    scope.launch {
        try {
            val decision = verifyServicePersonUseCase.execute(resolveCurrentUser()?.toServicePersonProfile())
            handleServicePersonVerificationDecision(
                decision = decision,
                onUseCachedFace = { cached ->
                    val orderNo = createOrderNo()
                    FaceVerificationEventTracker.trackEvent(
                        eventType = EventType.SERVICE_FACE_CACHE_HIT,
                        extras = mapOf(
                            "userId" to cached.user.userId,
                            "orderNo" to orderNo,
                            "sourcePhotoBase64Length" to cached.sourcePhotoBase64.length,
                        ),
                    )
                    launchSelfProvidedFaceVerificationWithBase64(
                        scope = scope,
                        context = context,
                        name = cached.user.userName,
                        idNo = cached.user.identityCardNumber,
                        orderNo = orderNo,
                        userId = cached.user.userId.toString(),
                        sourcePhotoBase64 = cached.sourcePhotoBase64,
                        beginVerification = beginVerification,
                        startVerificationWithRequest = startVerificationWithRequest,
                        onFailure = onVerificationFailure,
                    )
                },
                onDownloadAndCache = { download ->
                    val orderNo = createOrderNo()
                    FaceVerificationEventTracker.trackEvent(
                        eventType = EventType.SERVICE_REMOTE_FACE_SELECTED,
                        extras = FaceVerificationEventTracker.safeUrlExtras(download.sourcePhotoUrl) + mapOf(
                            "userId" to download.user.userId,
                            "orderNo" to orderNo,
                        ),
                    )
                    launchSelfProvidedFaceVerificationAndCache(
                        scope = scope,
                        context = context,
                        name = download.user.userName,
                        idNo = download.user.identityCardNumber,
                        orderNo = orderNo,
                        userId = download.user.userId.toString(),
                        cacheUserId = download.user.userId,
                        sourcePhotoUrl = download.sourcePhotoUrl,
                        faceDataSource = faceDataSource,
                        beginVerification = beginVerification,
                        startVerificationWithRequest = startVerificationWithRequest,
                        onFailure = { message -> onVerificationFailure(message, null) },
                    )
                },
                onRequireFaceSetup = {
                    FaceVerificationEventTracker.trackEvent(EventType.SERVICE_FACE_SETUP_REQUIRED)
                    onRequireFaceSetup()
                },
                onError = { message ->
                    onVerificationFailure(message, null)
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onVerificationFailure(e.message ?: "服务人员验证失败", e)
        }
    }
}

private fun User.toServicePersonProfile(): ServicePersonProfile {
    return ServicePersonProfile(
        userId = userId,
        userName = userName,
        identityCardNumber = identityCardNumber,
    )
}
