package com.ytone.longcare.common.utils

import android.content.Context
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test

class FaceVerificationManagerCancellationTest {

    private val repository = mockk<TencentFaceRepository>()
    private val callback = mockk<FaceVerifyCallback>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

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
    fun `startFaceVerification should rethrow cancellation exception`() = runTest {
        coEvery { repository.getAccessToken(any(), any()) } throws CancellationException("cancel")
        val manager = FaceVerificationManager(repository)

        val cancelled = try {
            manager.startFaceVerification(context, config, request, callback)
            null
        } catch (e: CancellationException) {
            e
        }
        assertNotNull(cancelled)

        verify(exactly = 0) { callback.onInitFailed(any()) }
        verify(exactly = 0) { callback.onVerifyFailed(any()) }
    }
}
