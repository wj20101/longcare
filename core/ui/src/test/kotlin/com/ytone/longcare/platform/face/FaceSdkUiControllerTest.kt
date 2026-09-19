package com.ytone.longcare.platform.face

import android.content.Context
import com.ytone.longcare.common.faceauth.*
import com.ytone.longcare.domain.faceauth.model.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FaceSdkUiControllerTest {
    private val verifier = mockk<FaceVerifier>(relaxed = true)
    private val controller = FaceSdkUiController(verifier)
    private val callback = slot<FaceVerifyCallback>()
    private val context = mockk<Context>()
    private val config = FaceVerificationConfig("app", "secret", "licence")
    private val request = FaceVerificationRequest("name", "id", "order", "user")

    @Test fun `terminal callbacks release once and late callbacks are ignored`() = runTest {
        coEvery { verifier.startFaceVerification(any(), any(), any(), capture(callback)) } just Runs
        val events = mutableListOf<FaceSdkEvent>()
        controller.start(context, config, request, events::add)
        callback.captured.onInitSuccess()
        callback.captured.onVerifyCancel()
        callback.captured.onVerifyFailed(null)
        controller.release()
        assertEquals(listOf(FaceSdkEvent.InitSuccess, FaceSdkEvent.Cancelled), events)
        verify(exactly = 1) { verifier.release() }
    }

    @Test fun `disposing while SDK is running suppresses result`() = runTest {
        coEvery { verifier.startFaceVerification(any(), any(), any(), capture(callback)) } just Runs
        val events = mutableListOf<FaceSdkEvent>()
        controller.start(context, config, request, events::add)
        controller.release()
        callback.captured.onVerifyCancel()
        assertTrue(events.isEmpty())
        verify(exactly = 1) { verifier.release() }
    }

    @Test fun `cancellation propagates and releases`() = runTest {
        coEvery { verifier.startFaceVerification(any(), any(), any(), any()) } throws CancellationException()
        try {
            controller.start(context, config, request) { fail("Unexpected event") }
            fail("Cancellation expected")
        } catch (_: CancellationException) {
            verify(exactly = 1) { verifier.release() }
        }
    }
}
