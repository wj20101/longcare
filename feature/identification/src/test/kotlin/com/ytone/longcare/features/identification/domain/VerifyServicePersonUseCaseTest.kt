package com.ytone.longcare.features.identification.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VerifyServicePersonUseCaseTest {
    private val sampleUser = ServicePersonProfile(
        userId = 1,
        userName = "Test User",
        identityCardNumber = "1111222233334444",
    )

    @Test
    fun `cached face present returns UseCachedFace without resolving remote`() = runBlocking {
        val gateway = FakeVerifyServicePersonDataGateway(
            cachedFace = "cached-base64",
            resolveFaceSourceProvider = { ServicePersonFaceSource.RequireFaceSetup },
        )
        val useCase = VerifyServicePersonUseCase(gateway)

        val decision = useCase.execute(sampleUser)

        assertTrue(decision is VerifyServicePersonDecision.UseCachedFace)
        decision as VerifyServicePersonDecision.UseCachedFace
        assertEquals(sampleUser, decision.user)
        assertEquals("cached-base64", decision.sourcePhotoBase64)
        assertEquals(listOf("readCachedFace"), gateway.callOrder)
    }

    @Test
    fun `cached face missing and remote face exists returns DownloadAndCache after readCachedFace`() = runBlocking {
        val gateway = FakeVerifyServicePersonDataGateway(
            cachedFace = null,
            resolveFaceSourceProvider = { ServicePersonFaceSource.RemoteFace("https://face.url") },
        )
        val useCase = VerifyServicePersonUseCase(gateway)

        val decision = useCase.execute(sampleUser)

        assertTrue(decision is VerifyServicePersonDecision.DownloadAndCache)
        decision as VerifyServicePersonDecision.DownloadAndCache
        assertEquals(sampleUser, decision.user)
        assertEquals("https://face.url", decision.sourcePhotoUrl)
        assertEquals(listOf("readCachedFace", "resolveFaceSource"), gateway.callOrder)
    }

    @Test
    fun `cached face missing and remote face missing returns RequireFaceSetup after readCachedFace`() = runBlocking {
        val gateway = FakeVerifyServicePersonDataGateway(
            cachedFace = null,
            resolveFaceSourceProvider = { ServicePersonFaceSource.RequireFaceSetup },
        )
        val useCase = VerifyServicePersonUseCase(gateway)

        val decision = useCase.execute(sampleUser)

        assertTrue(decision is VerifyServicePersonDecision.RequireFaceSetup)
        assertEquals(listOf("readCachedFace", "resolveFaceSource"), gateway.callOrder)
    }

    private class FakeVerifyServicePersonDataGateway(
        private val cachedFace: String?,
        private val resolveFaceSourceProvider: suspend () -> ServicePersonFaceSource,
    ) : VerifyServicePersonDataGateway {
        val callOrder = mutableListOf<String>()

        override suspend fun readCachedFace(userId: Int): String? {
            callOrder.add("readCachedFace")
            return cachedFace
        }

        override suspend fun resolveFaceSource(): ServicePersonFaceSource {
            callOrder.add("resolveFaceSource")
            return resolveFaceSourceProvider()
        }
    }
}
