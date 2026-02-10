package com.ytone.longcare.common.utils

import android.content.Context
import com.ytone.longcare.api.response.TencentAccessTokenResponse
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FaceVerificationManagerInputValidationTest {

    private val repository = mockk<TencentFaceRepository>()
    private val callback = mockk<FaceVerifyCallback>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val manager = FaceVerificationManager(repository)

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
}
