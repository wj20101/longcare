package com.ytone.longcare.features.identification.vm

import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.features.identification.domain.ServicePersonFaceSource
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDataGateway
import com.ytone.longcare.features.identification.domain.VerifyServicePersonUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class IdentificationServicePersonEntryFlowTest {
    private val textResolver = ResourceTextResolver(
        RuntimeEnvironment.getApplication(),
    )

    @Test
    fun `registered server face opens default verification only`() = runTest {
        val events = mutableListOf<String>()

        launchServicePersonVerification(
            scope = this,
            resolveCurrentUserId = { 123 },
            verifyServicePersonUseCase = useCase(ServicePersonFaceSource.RegisteredFaceAvailable),
            onRegisteredFaceAvailable = { events += "verify" },
            onRequireFaceSetup = { events += "setup" },
            onSessionInvalidated = { events += "logout" },
            onVerificationFailure = { _, _ -> events += "error" },
            textResolver = textResolver,
        ).join()

        assertThat(events).containsExactly("verify")
    }

    @Test
    fun `missing server face opens setup only`() = runTest {
        val events = mutableListOf<String>()

        launchServicePersonVerification(
            scope = this,
            resolveCurrentUserId = { 123 },
            verifyServicePersonUseCase = useCase(ServicePersonFaceSource.RequireFaceSetup),
            onRegisteredFaceAvailable = { events += "verify" },
            onRequireFaceSetup = { events += "setup" },
            onSessionInvalidated = { events += "logout" },
            onVerificationFailure = { _, _ -> events += "error" },
            textResolver = textResolver,
        ).join()

        assertThat(events).containsExactly("setup")
    }

    private fun useCase(source: ServicePersonFaceSource): VerifyServicePersonUseCase =
        VerifyServicePersonUseCase(
            object : VerifyServicePersonDataGateway {
                override suspend fun resolveFaceSource(): ServicePersonFaceSource = source

                override suspend fun clearLocalFaceArtifacts(userId: Int) = Unit
            },
        )
}
