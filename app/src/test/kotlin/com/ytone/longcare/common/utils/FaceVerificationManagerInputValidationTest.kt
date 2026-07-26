package com.ytone.longcare.common.utils

import android.content.Context
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import com.ytone.longcare.model.TicketInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class FaceVerificationManagerInputValidationTest {

    private val repository = mockk<TencentFaceRepository>()
    private val runtimeConfigProvider = mockk<RuntimeConfigProvider>(relaxed = true)
    private val callback = mockk<FaceVerifyCallback>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val manager = FaceVerificationManager(repository, runtimeConfigProvider)

    private val config = FaceVerificationConfig(
        appId = "app-id",
        secret = "secret",
        licence = "licence"
    )

    private val request = FaceVerificationRequest(
        name = "name",
        idNo = "id-no",
        orderNo = "order-no",
        userId = "user-id"
    )

    @Test
    fun `startFaceVerification should fail init when access token is blank`() = runTest {
        coEvery { repository.getAccessToken(any(), any()) } returns ApiResult.Success(
            TencentAccessTokenResponse(
                code = "0",
                msg = "ok",
                transactionTime = "2026-02-10T00:00:00Z",
                accessToken = "   "
            )
        )

        manager.startFaceVerification(context, config, request, callback)

        verify(exactly = 1) { callback.onInitFailed(any()) }
        verify(exactly = 0) { callback.onInitSuccess() }
        coVerify(exactly = 1) { repository.getAccessToken(config.appId, config.secret) }
        coVerify(exactly = 0) { repository.getSignTicket(any(), any()) }
        coVerify(exactly = 0) { repository.getApiTicket(any(), any(), any()) }
        coVerify(exactly = 0) {
            repository.getFaceId(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `startFaceVerification should hide getFaceId failure detail`() = runTest {
        coEvery { repository.getAccessToken(any(), any()) } returns ApiResult.Success(
            TencentAccessTokenResponse(
                code = "0",
                msg = "ok",
                transactionTime = "2026-02-10T00:00:00Z",
                accessToken = "access-token"
            )
        )
        coEvery { repository.getSignTicket(any(), any()) } returns ApiResult.Success(ticketResponse("sign-ticket"))
        coEvery {
            repository.getFaceId(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns ApiResult.Failure(code = 400101, message = "source photo is invalid")

        var initError: FaceVerifyError? = null
        val recordingCallback = recordingCallback(
            onInitFailed = { error -> initError = error },
        )

        manager.startFaceVerification(context, config, request, recordingCallback)

        val error = initError
        assertNotNull(error)
        val description = error!!.description.orEmpty()
        assertEquals("人脸核验准备失败，请稍后重试", description)
        assertFalse(description.contains("source photo is invalid"))
        assertFalse(description.contains("400101"))
    }

    private fun ticketResponse(value: String): TencentApiTicketResponse {
        return TencentApiTicketResponse(
            code = "0",
            msg = "ok",
            transactionTime = "2026-02-10T00:00:00Z",
            tickets = listOf(
                TicketInfo(
                    value = value,
                    expireTime = "2026-02-11T00:00:00Z",
                    expireIn = "3600",
                )
            ),
        )
    }

    private fun recordingCallback(
        onInitFailed: (FaceVerifyError?) -> Unit,
    ): FaceVerifyCallback {
        return object : FaceVerifyCallback {
            override fun onInitSuccess() = Unit
            override fun onInitFailed(error: FaceVerifyError?) = onInitFailed(error)
            override fun onVerifySuccess(result: com.ytone.longcare.domain.faceauth.model.FaceVerifyResult) = Unit
            override fun onVerifyFailed(error: FaceVerifyError?) = Unit
            override fun onVerifyCancel() = Unit
        }
    }
}
