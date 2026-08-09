package com.ytone.longcare.features.identification.vm

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
    resolveCurrentUser: suspend () -> User?,
    verifyServicePersonUseCase: VerifyServicePersonUseCase,
    createOrderNo: () -> String,
    faceDataSource: IdentificationFaceDataSource,
    beginVerification: (VerificationType) -> Unit,
    startVerificationWithRequest: suspend (FaceVerificationRequest) -> Unit,
    onRequireFaceSetup: () -> Unit,
    onVerificationFailure: (String, Throwable?) -> Unit,
) {
    scope.launch {
        try {
            val decision = verifyServicePersonUseCase.execute(resolveCurrentUser()?.toServicePersonProfile())
            handleServicePersonVerificationDecision(
                decision = decision,
                scope = scope,
                createOrderNo = createOrderNo,
                faceDataSource = faceDataSource,
                beginVerification = beginVerification,
                startVerificationWithRequest = startVerificationWithRequest,
                onRequireFaceSetup = onRequireFaceSetup,
                onVerificationFailure = onVerificationFailure,
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
