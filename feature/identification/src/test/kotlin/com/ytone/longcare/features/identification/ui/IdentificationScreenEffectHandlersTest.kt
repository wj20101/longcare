package com.ytone.longcare.features.identification.ui

import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLauncher
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLaunchRequest
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationUiAction
import com.ytone.longcare.features.identification.vm.IdentificationUiEffect
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationScreenEffectHandlersTest {
    @Test
    fun `string result is processed before one acknowledgement and null is ignored`() {
        val events = mutableListOf<String>()

        assertFalse(consumeStringResult(null, events::add) { events += "ack" })
        assertTrue(consumeStringResult("value", events::add) { events += "ack" })

        assertEquals(listOf("value", "ack"), events)
    }

    @Test
    fun `default verification success and failure reset and acknowledge once`() {
        val success = mutableListOf<String>()
        consumeDefaultFaceVerificationResult(
            result = true,
            onVerified = { success += "verified" },
            onReset = { success += "reset" },
            acknowledge = { success += "ack" },
        )
        val failure = mutableListOf<String>()
        consumeDefaultFaceVerificationResult(
            result = false,
            onVerified = { failure += "verified" },
            onReset = { failure += "reset" },
            acknowledge = { failure += "ack" },
        )

        assertEquals(listOf("verified", "reset", "ack"), success)
        assertEquals(listOf("reset", "ack"), failure)
    }

    @Test
    fun `pending action dispatches before acknowledgement`() {
        val events = mutableListOf<String>()
        val action = IdentificationUiAction(
            id = 9L,
            effect = IdentificationUiEffect.NavigateToDefaultFaceVerification(OrderKey(3L)),
        )

        dispatchPendingUiAction(
            action = action,
            onDefaultFaceVerification = { events += "default:${it.orderKey.orderId}" },
            onManualFaceCapture = { events += "manual" },
            onMessage = { events += "message" },
            acknowledge = { events += "ack:$it" },
        )

        assertEquals(listOf("default:3", "ack:9"), events)
    }

    @Test
    fun `photo completion navigates once on success and only resets on error`() {
        val success = mutableListOf<String>()
        val error = mutableListOf<String>()

        assertTrue(handlePhotoUploadCompletion(
            state = PhotoUploadState.Success,
            onSuccess = { success += "navigate" },
            onReset = { success += "reset" },
        ))
        assertTrue(handlePhotoUploadCompletion(
            state = PhotoUploadState.Error("failed"),
            onSuccess = { error += "navigate" },
            onReset = { error += "reset" },
        ))

        assertEquals(listOf("navigate", "reset"), success)
        assertEquals(listOf("reset"), error)
    }

    @Test
    fun `face verification success updates only its active person`() {
        val events = mutableListOf<String>()
        val state = FaceVerificationState.Success(FaceVerifyResult(isSuccess = true))

        handleFaceVerificationCompletion(
            state = state,
            verificationType = VerificationType.ELDER,
            onServicePersonVerified = { events += "service" },
            onElderVerified = { events += "elder" },
            onPersistElderVerification = { events += "persist" },
        )

        assertEquals(listOf("elder", "persist"), events)
    }

    @Test
    fun `SDK request is launched once and events keep the request id`() = runTest {
        val launchRequest = testLaunchRequest()
        val events = mutableListOf<String>()
        val launcher = IdentificationFaceSdkLauncher { request, onEvent ->
            events += "launch:${request.id}"
            onEvent(FaceSdkEvent.InitSuccess)
        }

        assertTrue(deliverFaceSdkLaunchRequest(
            request = launchRequest,
            launcher = launcher,
            onEvent = { id, _ -> events += "event:$id" },
            acknowledge = { events += "ack:$it" },
        ))

        assertEquals(listOf("launch:17", "event:17", "ack:17"), events)
    }

    @Test
    fun `missing SDK request has no launcher or acknowledgement side effect`() = runTest {
        val events = mutableListOf<String>()

        val handled = deliverFaceSdkLaunchRequest(
            request = null,
            launcher = IdentificationFaceSdkLauncher { _, _ -> events += "launch" },
            onEvent = { _, _ -> events += "event" },
            acknowledge = { events += "ack" },
        )

        assertFalse(handled)
        assertTrue(events.isEmpty())
    }

    private fun testLaunchRequest() = IdentificationFaceSdkLaunchRequest(
        id = 17L,
        config = FaceVerificationConfig("app", "secret", "licence"),
        request = FaceVerificationRequest("name", "id", "order", "user"),
    )
}
