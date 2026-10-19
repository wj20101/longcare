package com.ytone.longcare.common.utils

import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceVerifySdk
import com.tencent.cloud.huiyansdkface.facelight.api.listeners.WbCloudFaceVerifyLoginListener
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerificationSession
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class FaceVerificationManagerSessionTest {
    private val sdk = mockk<WbCloudFaceVerifySdk>(relaxed = true)
    private val login = slot<WbCloudFaceVerifyLoginListener>()
    private val repository = mockk<TencentFaceRepository>()
    private val runtime = mockk<RuntimeConfigProvider>(relaxed = true)
    private val callback = mockk<FaceVerifyCallback>(relaxed = true)
    private val session = object : FaceVerificationSession {
        override val sessionGeneration = MutableStateFlow<Long?>(1L)
    }
    private val config = FaceVerificationConfig("app", "licence", 1L)
    private val request = FaceVerificationRequest(name = null, idNo = null, orderNo = "order", userId = "elderly-99", sourcePhotoStr = "photo")
    private val token = ApiResult.Success(TencentAccessTokenResponse("0", "ok", "time", accessToken = "token"))
    private val ticket = ApiResult.Success(TencentApiTicketResponse("0", "ok", "time", listOf(TicketInfo("ticket", "time", "3600"))))

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkStatic(WbCloudFaceVerifySdk::class)
        every { WbCloudFaceVerifySdk.getInstance() } returns sdk
        every { sdk.initSdk(any(), any(), capture(login)) } just Runs
        coEvery { repository.getAccessToken(any()) } returns token
        coEvery { repository.getSignTicket(any(), any(), any()) } returns ticket
        coEvery { repository.getApiTicket(any(), any(), any(), any()) } returns ticket
        coEvery { repository.getFaceId(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            ApiResult.Success(TencentFaceIdResponse("0", "ok", "time", result = FaceIdResult(faceId = "face")))
    }

    @After
    fun cleanup() {
        unmockkStatic(WbCloudFaceVerifySdk::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `normal elderly photo flow preserves payload and launches SDK once`() = runTest {
        val manager = FaceVerificationManager(repository, runtime, session, backgroundScope)
        manager.startFaceVerification(RuntimeEnvironment.getApplication(), config, request, callback)
        login.captured.onLoginSuccess()
        verify(exactly = 1) { sdk.startWbFaceVerifySdk(any(), any()) }
        verify(exactly = 1) { callback.onInitSuccess() }
        coVerify { repository.getFaceId("app", "order", null, null, "elderly-99", any(), any(), "photo", "2", 1L) }
        coVerify { repository.getApiTicket("app", "token", "elderly-99", 1L) }
        manager.release()
        login.captured.onLoginSuccess()
        verify(exactly = 1) { sdk.startWbFaceVerifySdk(any(), any()) }
    }

    @Test
    fun `logout after initialization releases SDK and blocks delayed login callback`() = runTest {
        val manager = FaceVerificationManager(repository, runtime, session, backgroundScope)
        manager.startFaceVerification(RuntimeEnvironment.getApplication(), config, request, callback)
        session.sessionGeneration.value = null
        runCurrent()
        login.captured.onLoginSuccess()
        verify(exactly = 1) { sdk.release() }
        verify(exactly = 0) { sdk.startWbFaceVerifySdk(any(), any()) }
        verify(exactly = 0) { callback.onInitSuccess() }
        verify(exactly = 1) { callback.onVerifyCancel() }
    }

    @Test
    fun `releasing from init callback prevents vendor UI launch`() = runTest {
        val manager = FaceVerificationManager(repository, runtime, session, backgroundScope)
        every { callback.onInitSuccess() } answers { manager.release() }
        manager.startFaceVerification(RuntimeEnvironment.getApplication(), config, request, callback)
        login.captured.onLoginSuccess()
        verify(exactly = 0) { sdk.startWbFaceVerifySdk(any(), any()) }
    }

    @Test
    fun `logout cancels suspended preparation before SDK initialization`() = runTest {
        val started = CompletableDeferred<Unit>()
        coEvery { repository.getAccessToken(any()) } coAnswers {
            started.complete(Unit)
            awaitCancellation()
        }
        val manager = FaceVerificationManager(repository, runtime, session, backgroundScope)
        val pending = async { manager.startFaceVerification(RuntimeEnvironment.getApplication(), config, request, callback) }
        started.await()
        session.sessionGeneration.value = 2L
        runCurrent()
        org.junit.Assert.assertTrue(
            "Old preparation must be cancelled",
            runCatching { pending.await() }.exceptionOrNull() is CancellationException,
        )
        verify(exactly = 0) { sdk.initSdk(any(), any(), any()) }
        verify(exactly = 1) { callback.onVerifyCancel() }
    }
}
