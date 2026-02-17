package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.domain.ServicePersonProfile
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
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
                    launchSelfProvidedFaceVerificationWithBase64(
                        scope = scope,
                        context = context,
                        name = cached.user.userName,
                        idNo = cached.user.identityCardNumber,
                        orderNo = createOrderNo(),
                        userId = cached.user.userId.toString(),
                        sourcePhotoBase64 = cached.sourcePhotoBase64,
                        beginVerification = beginVerification,
                        startVerificationWithRequest = startVerificationWithRequest,
                        onFailure = onVerificationFailure,
                    )
                },
                onDownloadAndCache = { download ->
                    launchSelfProvidedFaceVerificationAndCache(
                        scope = scope,
                        context = context,
                        name = download.user.userName,
                        idNo = download.user.identityCardNumber,
                        orderNo = createOrderNo(),
                        userId = download.user.userId.toString(),
                        sourcePhotoUrl = download.sourcePhotoUrl,
                        faceDataSource = faceDataSource,
                        resolveCurrentUser = resolveCurrentUser,
                        beginVerification = beginVerification,
                        startVerificationWithRequest = startVerificationWithRequest,
                        onFailure = { message -> onVerificationFailure(message, null) },
                    )
                },
                onRequireFaceSetup = onRequireFaceSetup,
                onError = { message -> onVerificationFailure(message, null) }
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
