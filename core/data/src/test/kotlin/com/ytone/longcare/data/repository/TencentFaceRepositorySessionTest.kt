package com.ytone.longcare.data.repository

import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TencentFaceRepositorySessionTest {
    private val systemApi = mockk<LongCareApiService>()
    private val tencentApi = mockk<TencentFaceApiService>()
    private val session = UserSessionTracker().apply { observe(User(userId = 7, token = "operator-token")) }
    private val source = SystemConfigModel(thirdKeyStr = """{"TxFaceAppId":"app","TxFaceAppSecret":"ephemeral-secret","TxFaceAppLicence":"licence"}""")
    private val token = TencentAccessTokenResponse("0", "ok", "time", accessToken = "token", expireIn = "3600")
    private val ticket = TencentApiTicketResponse("0", "ok", "time", listOf(TicketInfo("ticket", "time", "3600")))

    @Test
    fun `token cache hit avoids secret fetch while nonce remains per subject and attempt`() = runTest {
        val manager = SystemConfigManager(RuntimeEnvironment.getApplication(), backgroundScope, Moshi.Builder().build(), systemApi, session)
        manager.saveSystemConfig(source)
        val config = requireNotNull(manager.getFaceVerificationConfig())
        val repository = TencentFaceRepositoryImpl(tencentApi, manager, session)
        coEvery { systemApi.getSystemConfig() } returns ApiResult.Success(source)
        coEvery { tencentApi.getAccessToken("app", "ephemeral-secret", any(), any()) } returns ApiResult.Success(token)
        coEvery { tencentApi.getSignTicket(any(), any(), any(), any()) } returns ApiResult.Success(ticket)
        coEvery { tencentApi.getApiTicket(any(), any(), any(), any(), any()) } returns ApiResult.Success(ticket)
        repeat(2) {
            assertTrue(repository.getAccessToken(config) is ApiResult.Success)
            repository.getSignTicket("app", "token", config.sessionGeneration)
            repository.getApiTicket("app", "token", "elderly-99", config.sessionGeneration)
        }
        coVerify(exactly = 1) { systemApi.getSystemConfig() }
        coVerify(exactly = 1) { tencentApi.getAccessToken(any(), any(), any(), any()) }
        coVerify(exactly = 1) { tencentApi.getSignTicket(any(), any(), any(), any()) }
        coVerify(exactly = 2) { tencentApi.getApiTicket("app", "token", "NONCE", any(), "elderly-99") }
        assertEquals("", manager.getThirdKeySync()?.txFaceAppSecret)
        session.beginChange()
        session.finishChange(User(userId = 7, token = "operator-token"))
        val nextConfig = requireNotNull(manager.getFaceVerificationConfig())
        repository.getAccessToken(nextConfig)
        coVerify(exactly = 2) { systemApi.getSystemConfig() }
    }

    @Test
    fun `switch while fetching fresh secret prevents Tencent request and stale config overwrite`() = runTest {
        val manager = SystemConfigManager(RuntimeEnvironment.getApplication(), backgroundScope, Moshi.Builder().build(), systemApi, session)
        manager.saveSystemConfig(source)
        val config = requireNotNull(manager.getFaceVerificationConfig())
        val repository = TencentFaceRepositoryImpl(tencentApi, manager, session)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { systemApi.getSystemConfig() } coAnswers {
            started.complete(Unit)
            finish.await()
            ApiResult.Success(source.copy(companyName = "stale company"))
        }
        val pending = async { repository.getAccessToken(config) }
        started.await()
        session.beginChange()
        session.finishChange(User(userId = 8, token = "new-token"))
        finish.complete(Unit)
        org.junit.Assert.assertTrue(
            "Old session must be cancelled",
            runCatching { pending.await() }.exceptionOrNull() is CancellationException,
        )
        coVerify(exactly = 0) { tencentApi.getAccessToken(any(), any(), any(), any()) }
        assertNotEquals("stale company", manager.getSystemConfig()?.companyName)
    }

    @Test
    fun `late nonce and faceId results are rejected after logout`() = runTest {
        val manager = SystemConfigManager(RuntimeEnvironment.getApplication(), backgroundScope, Moshi.Builder().build(), systemApi, session)
        val repository = TencentFaceRepositoryImpl(tencentApi, manager, session)
        val generation = requireNotNull(session.sessionGeneration.value)
        coEvery { tencentApi.getApiTicket(any(), any(), any(), any(), any()) } coAnswers {
            session.beginChange()
            ApiResult.Success(ticket)
        }
        org.junit.Assert.assertTrue(
            "Late nonce must be rejected",
            runCatching { repository.getApiTicket("app", "token", "elderly", generation) }.exceptionOrNull() is CancellationException,
        )
        session.finishChange(User(userId = 7))
        val next = requireNotNull(session.sessionGeneration.value)
        coEvery { tencentApi.getFaceId(any(), any()) } coAnswers {
            session.beginChange()
            ApiResult.Success(TencentFaceIdResponse("0", "ok", "time", result = FaceIdResult(faceId = "face")))
        }
        org.junit.Assert.assertTrue(
            "Late faceId must be rejected",
            runCatching { repository.getFaceId("app", "order", null, null, "elderly", "sign", "nonce", "photo", "2", next) }.exceptionOrNull() is CancellationException,
        )
    }
}
