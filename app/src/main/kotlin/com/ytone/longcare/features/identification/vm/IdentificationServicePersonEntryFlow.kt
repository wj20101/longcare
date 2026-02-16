package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.features.identification.domain.ServicePersonProfile
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import com.ytone.longcare.models.protos.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchServicePersonVerification(
    scope: CoroutineScope,
    context: Context,
    resolveCurrentUser: suspend () -> User?,
    verifyServicePersonUseCase: VerifyServicePersonUseCase,
    createOrderNo: () -> String,
    startVerificationWithBase64: (Context, String, String, String, String, String) -> Unit,
    startVerificationAndCache: (Context, String, String, String, String, String) -> Unit,
    onRequireFaceSetup: () -> Unit,
    onError: (String) -> Unit,
) {
    scope.launch {
        try {
            val decision = verifyServicePersonUseCase.execute(resolveCurrentUser()?.toServicePersonProfile())
            handleServicePersonVerificationDecision(
                decision = decision,
                onUseCachedFace = { cached ->
                    startVerificationWithBase64(
                        context,
                        cached.user.userName,
                        cached.user.identityCardNumber,
                        createOrderNo(),
                        cached.user.userId.toString(),
                        cached.sourcePhotoBase64,
                    )
                },
                onDownloadAndCache = { download ->
                    startVerificationAndCache(
                        context,
                        download.user.userName,
                        download.user.identityCardNumber,
                        createOrderNo(),
                        download.user.userId.toString(),
                        download.sourcePhotoUrl,
                    )
                },
                onRequireFaceSetup = onRequireFaceSetup,
                onError = onError
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e.message ?: "服务人员验证失败")
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
