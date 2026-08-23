package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.feature.identification.R
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
    textResolver: ResourceTextResolver,
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
                textResolver = textResolver,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onVerificationFailure(
                textResolver.text(R.string.identification_face_verification_failed),
                e,
            )
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
