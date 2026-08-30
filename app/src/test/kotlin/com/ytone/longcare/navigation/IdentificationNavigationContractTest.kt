package com.ytone.longcare.navigation

import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.model.OrderKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IdentificationNavigationContractTest {
    @Test
    fun `identification route preserves order parameters through serialization`() {
        val expected = IdentificationRoute(
            orderParams = OrderNavParams(orderId = 987654321L, planId = 42),
        )

        val encoded = Json.encodeToString(IdentificationRoute.serializer(), expected)
        val decoded = Json.decodeFromString(IdentificationRoute.serializer(), encoded)

        assertEquals(expected, decoded)
        assertEquals(expected.orderParams, decoded.orderParams)
    }

    @Test
    fun `order nav type round trip preserves identification back stack arguments`() {
        val expected = OrderNavParams(orderId = 73L, planId = 5)

        val encoded = OrderNavParamsNavType.serializeAsValue(expected)

        assertEquals(expected, OrderNavParamsNavType.parseValue(encoded))
    }

    @Test
    fun `identification result keys remain stable and action acknowledgements clear them`() {
        assertEquals("captured_image_uri", NavigationConstants.CAPTURED_IMAGE_URI_KEY)
        assertEquals("face_image_path", NavigationConstants.FACE_IMAGE_PATH_KEY)
        assertEquals(
            "default_face_verification_result",
            NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
        )
        val savedStateHandle = SavedStateHandle()
        val actions = createActions(savedStateHandle)
        savedStateHandle[NavigationConstants.CAPTURED_IMAGE_URI_KEY] = "content://elder-photo"
        savedStateHandle[NavigationConstants.FACE_IMAGE_PATH_KEY] = "/tmp/face.jpg"
        savedStateHandle[NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY] = true

        assertEquals("content://elder-photo", actions.capturedImageUriFlow.value)
        assertEquals("/tmp/face.jpg", actions.faceImagePathFlow.value)
        assertEquals(true, actions.defaultFaceVerificationResultFlow.value)

        actions.clearCapturedImageUri()
        actions.clearFaceImagePath()
        actions.clearDefaultFaceVerificationResult()

        assertNull(actions.capturedImageUriFlow.value)
        assertNull(actions.faceImagePathFlow.value)
        assertNull(actions.defaultFaceVerificationResultFlow.value)
        assertNull(savedStateHandle.get<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY))
        assertNull(savedStateHandle.get<String>(NavigationConstants.FACE_IMAGE_PATH_KEY))
        assertNull(
            savedStateHandle.get<Boolean>(
                NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
            ),
        )
    }

    @Test
    fun `system and page back share one action and success dispatches one destination`() {
        val events = mutableListOf<String>()
        val actions = createIdentificationRouteActions(
            savedStateHandle = SavedStateHandle(),
            onNavigateBack = { events += "back" },
            onNavigateToCamera = { events += "camera" },
            onNavigateToManualFaceCapture = { events += "manual" },
            onNavigateToDefaultFaceVerification = { events += "default:${it.orderId}" },
            onNavigateToSelectService = { events += "select:${it.orderId}" },
        )

        val systemBackAction = actions.onNavigateBack
        val pageBackAction = actions.onNavigateBack
        systemBackAction()
        pageBackAction()
        actions.onNavigateToSelectService(OrderKey(orderId = 23L))

        assertEquals(listOf("back", "back", "select:23"), events)
    }

    private fun createActions(savedStateHandle: SavedStateHandle) =
        createIdentificationRouteActions(
            savedStateHandle = savedStateHandle,
            onNavigateBack = {},
            onNavigateToCamera = {},
            onNavigateToManualFaceCapture = {},
            onNavigateToDefaultFaceVerification = {},
            onNavigateToSelectService = {},
        )
}
