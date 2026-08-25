package com.ytone.longcare.features.identification.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VerifyServicePersonUseCaseTest {
    private val sampleUserId = 1

    @Test
    fun `registered face enables the current default verification flow`() = runTest {
        val gateway = FakeVerifyServicePersonDataGateway(
            source = ServicePersonFaceSource.RegisteredFaceAvailable,
        )

        val decision = VerifyServicePersonUseCase(gateway).execute(sampleUserId)

        assertThat(decision).isEqualTo(VerifyServicePersonDecision.VerifyRegisteredFace)
        assertThat(gateway.clearedUserIds).isEmpty()
    }

    @Test
    fun `missing server face clears local artifacts and requires setup`() = runTest {
        val gateway = FakeVerifyServicePersonDataGateway(
            source = ServicePersonFaceSource.RequireFaceSetup,
        )

        val decision = VerifyServicePersonUseCase(gateway).execute(sampleUserId)

        assertThat(decision).isEqualTo(VerifyServicePersonDecision.RequireFaceSetup)
        assertThat(gateway.clearedUserIds).containsExactly(sampleUserId)
    }

    @Test
    fun `session invalidation does not clear cache or start another flow`() = runTest {
        val gateway = FakeVerifyServicePersonDataGateway(
            source = ServicePersonFaceSource.SessionInvalidated,
        )

        val decision = VerifyServicePersonUseCase(gateway).execute(sampleUserId)

        assertThat(decision).isEqualTo(VerifyServicePersonDecision.SessionInvalidated)
        assertThat(gateway.clearedUserIds).isEmpty()
    }

    @Test
    fun `missing current user stops before resolving face source`() = runTest {
        val gateway = FakeVerifyServicePersonDataGateway(
            source = ServicePersonFaceSource.RequireFaceSetup,
        )

        val decision = VerifyServicePersonUseCase(gateway).execute(null)

        assertThat(decision).isEqualTo(
            VerifyServicePersonDecision.Error(
                ServicePersonVerificationFailure.CurrentUserUnavailable,
            ),
        )
        assertThat(gateway.resolveCalls).isEqualTo(0)
    }

    private class FakeVerifyServicePersonDataGateway(
        private val source: ServicePersonFaceSource,
    ) : VerifyServicePersonDataGateway {
        var resolveCalls = 0
        val clearedUserIds = mutableListOf<Int>()

        override suspend fun resolveFaceSource(): ServicePersonFaceSource {
            resolveCalls += 1
            return source
        }

        override suspend fun clearLocalFaceArtifacts(userId: Int) {
            clearedUserIds += userId
        }
    }
}
