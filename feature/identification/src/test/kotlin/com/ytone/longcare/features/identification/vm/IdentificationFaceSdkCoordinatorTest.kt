package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLaunchRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentificationFaceSdkCoordinatorTest {
    @Test
    fun `prepared request remains the only pending launch until its id is consumed`() = runTest {
        val coordinator = coordinatorWithConfig()

        coordinator.prepareStandard(testRequest())
        val launch = requireNotNull(coordinator.launchRequest.value)

        assertEquals(launch, coordinator.launchRequest.value)
        coordinator.consume(launch.id + 1)
        assertEquals(launch, coordinator.launchRequest.value)
        coordinator.consume(launch.id)
        coordinator.consume(launch.id)
        assertNull(coordinator.launchRequest.value)
    }

    @Test
    fun `consuming UI request keeps callback routing active until terminal event`() = runTest {
        val coordinator = coordinatorWithConfig()
        val request = testRequest()
        val receivedEvents = mutableListOf<FaceSdkEvent>()

        coordinator.prepareStandard(request)
        val launch = requireNotNull(coordinator.launchRequest.value)
        coordinator.consume(launch.id)
        assertNull(coordinator.launchRequest.value)

        coordinator.dispatch(
            id = launch.id,
            event = FaceSdkEvent.InitSuccess,
            onStandard = receivedEvents::add,
            onFaceSetup = { _, _ -> error("unexpected setup event") },
        )
        coordinator.dispatch(
            id = launch.id,
            event = FaceSdkEvent.Cancelled,
            onStandard = receivedEvents::add,
            onFaceSetup = { _, _ -> error("unexpected setup event") },
        )
        coordinator.dispatch(
            id = launch.id,
            event = FaceSdkEvent.InitSuccess,
            onStandard = receivedEvents::add,
            onFaceSetup = { _, _ -> error("unexpected setup event") },
        )

        assertEquals(listOf(FaceSdkEvent.InitSuccess, FaceSdkEvent.Cancelled), receivedEvents)
    }

    @Test
    fun `missing config reports error without exposing SDK request`() = runTest {
        var configMissingCount = 0
        val coordinator = IdentificationFaceSdkCoordinator(
            configProvider = object : FaceVerificationConfigProvider {
                override suspend fun getFaceVerificationConfig(): FaceVerificationConfig? = null
            },
            onStandardConfigMissing = { configMissingCount++ },
            onFaceSetupConfigMissing = {},
        )

        coordinator.prepareStandard(testRequest())

        assertEquals(1, configMissingCount)
        assertNull(coordinator.launchRequest.value)
    }

    @Test
    fun `missing refreshed config invalidates an older launch and callback route`() = runTest {
        var configCall = 0
        var configMissingCount = 0
        val coordinator = IdentificationFaceSdkCoordinator(
            configProvider = object : FaceVerificationConfigProvider {
                override suspend fun getFaceVerificationConfig(): FaceVerificationConfig? =
                    if (configCall++ == 0) {
                        FaceVerificationConfig("app", "secret", "licence")
                    } else {
                        null
                    }
            },
            onStandardConfigMissing = { configMissingCount++ },
            onFaceSetupConfigMissing = {},
        )
        val receivedEvents = mutableListOf<FaceSdkEvent>()

        coordinator.prepareStandard(testRequest())
        val staleLaunch = requireNotNull(coordinator.launchRequest.value)
        coordinator.prepareStandard(testRequest())
        coordinator.dispatch(
            id = staleLaunch.id,
            event = FaceSdkEvent.InitSuccess,
            onStandard = receivedEvents::add,
            onFaceSetup = { _, _ -> error("unexpected setup event") },
        )

        assertEquals(1, configMissingCount)
        assertNull(coordinator.launchRequest.value)
        assertEquals(emptyList<FaceSdkEvent>(), receivedEvents)
    }

    @Test
    fun `newer launch invalidates callbacks from the previous request id`() = runTest {
        val coordinator = coordinatorWithConfig()
        val receivedEvents = mutableListOf<FaceSdkEvent>()

        coordinator.prepareStandard(testRequest())
        val staleLaunch = requireNotNull(coordinator.launchRequest.value)
        coordinator.prepareStandard(testRequest().copy(orderNo = "new-order"))
        val activeLaunch = requireNotNull(coordinator.launchRequest.value)

        coordinator.dispatch(
            id = staleLaunch.id,
            event = FaceSdkEvent.InitSuccess,
            onStandard = receivedEvents::add,
            onFaceSetup = { _, _ -> error("unexpected setup event") },
        )
        coordinator.dispatch(
            id = activeLaunch.id,
            event = FaceSdkEvent.InitSuccess,
            onStandard = receivedEvents::add,
            onFaceSetup = { _, _ -> error("unexpected setup event") },
        )

        assertEquals(listOf(FaceSdkEvent.InitSuccess), receivedEvents)
    }

    @Test
    fun `success failure and cancellation are terminal for their request`() = runTest {
        val terminalEvents = listOf(
            FaceSdkEvent.VerifySuccess(FaceVerifyResult(isSuccess = true)),
            FaceSdkEvent.VerifyFailed(error = null),
            FaceSdkEvent.Cancelled,
        )

        terminalEvents.forEach { terminalEvent ->
            val coordinator = coordinatorWithConfig()
            val receivedEvents = mutableListOf<FaceSdkEvent>()
            coordinator.prepareStandard(testRequest())
            val launch = requireNotNull(coordinator.launchRequest.value)

            coordinator.dispatch(
                id = launch.id,
                event = terminalEvent,
                onStandard = receivedEvents::add,
                onFaceSetup = { _, _ -> error("unexpected setup event") },
            )
            coordinator.dispatch(
                id = launch.id,
                event = FaceSdkEvent.InitSuccess,
                onStandard = receivedEvents::add,
                onFaceSetup = { _, _ -> error("unexpected setup event") },
            )

            assertEquals(listOf(terminalEvent), receivedEvents)
        }
    }

    private fun coordinatorWithConfig() = IdentificationFaceSdkCoordinator(
        configProvider = object : FaceVerificationConfigProvider {
            override suspend fun getFaceVerificationConfig() =
                FaceVerificationConfig("app", "secret", "licence")
        },
        onStandardConfigMissing = {},
        onFaceSetupConfigMissing = {},
    )

    private fun testRequest() = FaceVerificationRequest(
        name = "name",
        idNo = "id",
        orderNo = "order",
        userId = "user",
    )
}
