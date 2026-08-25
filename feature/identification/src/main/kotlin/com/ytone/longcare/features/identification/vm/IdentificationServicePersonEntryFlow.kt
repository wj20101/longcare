package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun launchServicePersonVerification(
    scope: CoroutineScope,
    resolveCurrentUserId: suspend () -> Int?,
    verifyServicePersonUseCase: VerifyServicePersonUseCase,
    onRegisteredFaceAvailable: () -> Unit,
    onRequireFaceSetup: () -> Unit,
    onSessionInvalidated: () -> Unit,
    onVerificationFailure: (String, Throwable?) -> Unit,
    textResolver: ResourceTextResolver,
): Job =
    scope.launch {
        try {
            val decision = verifyServicePersonUseCase.execute(resolveCurrentUserId())
            handleServicePersonVerificationDecision(
                decision = decision,
                onRegisteredFaceAvailable = onRegisteredFaceAvailable,
                onRequireFaceSetup = onRequireFaceSetup,
                onSessionInvalidated = onSessionInvalidated,
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
