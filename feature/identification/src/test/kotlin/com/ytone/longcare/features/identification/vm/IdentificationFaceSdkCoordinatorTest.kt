package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentificationFaceSdkCoordinatorTest {
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
            override val sessionGeneration = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
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
            override val sessionGeneration = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
                override suspend fun getFaceVerificationConfig(): FaceVerificationConfig? =
                    if (configCall++ == 0) {
                        FaceVerificationConfig("app", "licence", 1L)
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
    fun `old screen cannot publish a suspended launch or restart under a new session`() = runTest {
        val generation = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finish = kotlinx.coroutines.CompletableDeferred<Unit>()
        var configCalls = 0
        val coordinator = IdentificationFaceSdkCoordinator(
            configProvider = object : FaceVerificationConfigProvider {
                override val sessionGeneration = generation
                override suspend fun getFaceVerificationConfig(): FaceVerificationConfig {
                    configCalls++
                    started.complete(Unit)
                    finish.await()
                    return FaceVerificationConfig("app", "licence", 2L)
                }
            },
            onStandardConfigMissing = { error("stale request must be silent") },
            onFaceSetupConfigMissing = {},
        )
        val pending = async { coordinator.prepareStandard(testRequest()) }
        started.await()
        generation.value = 2L
        finish.complete(Unit)
        pending.await()
        assertNull(coordinator.launchRequest.value)
        coordinator.prepareStandard(testRequest())
        assertEquals(1, configCalls)
    }

    private fun coordinatorWithConfig() = IdentificationFaceSdkCoordinator(
        configProvider = object : FaceVerificationConfigProvider {
            override val sessionGeneration = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
            override suspend fun getFaceVerificationConfig() =
                FaceVerificationConfig("app", "licence", 1L)
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
