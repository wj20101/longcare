package com.ytone.longcare.platform.sales

import android.app.Activity
import com.ytone.longcare.integration.qlz.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesSdkUiControllerTest {
    private val activity = mockk<Activity>()
    private val client = mockk<QlzSdkClient>()
    private val drivers = mutableListOf<QlzEvaluationDriver>()
    private val callbacks = mutableListOf<(QlzEvaluationDriverEvent) -> Unit>()
    private var releases = 0

    private fun controller(): SalesSdkUiController {
        every { client.createEvaluationSession(any(), any(), any(), any()) } answers {
            val driver = mockk<QlzEvaluationDriver>(relaxed = true)
            every { driver.authorize(any(), any()) } answers {
                callbacks += secondArg<(QlzEvaluationDriverEvent) -> Unit>()
            }
            drivers += driver
            QlzEvaluationSessionCreation.Ready(
                QlzEvaluationSession(
                    driverFactory = QlzEvaluationDriverFactory { driver },
                    uploadContext = secondArg(),
                    onEvent = thirdArg(),
                    onStateChanged = arg(3),
                    releaseLease = { releases++ },
                )
            )
        }
        return SalesSdkUiController(client)
    }

    private fun SalesSdkUiController.start() =
        startEvaluation(activity, "test-token", QlzEvaluationUploadContext()) {}

    @Test
    fun `rescan uses existing session and clears candidates once`() {
        val controller = controller()
        controller.start()
        callbacks.single()(QlzEvaluationDriverEvent.Authorized)
        callbacks.single()(QlzEvaluationDriverEvent.DevicesChanged(
            listOf(QlzDeviceOption("device-1", "Test device", "••••"))
        ))

        controller.start()
        controller.start()

        assertEquals(QlzEvaluationStage.SCANNING, controller.uiState.value.stage)
        assertTrue(controller.uiState.value.devices.isEmpty())
        assertEquals(1, drivers.size)
        verify(exactly = 2) { drivers.single().startScan() }
        assertEquals(0, releases)
    }

    @Test
    fun `permission recovery releases old session and checks environment through factory again`() {
        val controller = controller()
        controller.start()
        callbacks.single()(QlzEvaluationDriverEvent.Failed(
            code = 0,
            issue = QlzEvaluationIssue.PERMISSION_REQUIRED,
            recoveryAction = QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT,
        ))

        controller.start()
        callbacks.last()(QlzEvaluationDriverEvent.Authorized)

        assertEquals(2, drivers.size)
        assertEquals(1, releases)
        verify(exactly = 1) { drivers.first().close() }
        assertEquals(QlzEvaluationStage.SCANNING, controller.uiState.value.stage)
    }

    @Test
    fun `late permission grant cannot create a session while host is stopped`() {
        val controller = controller()
        controller.onHostStopped()
        controller.start()
        assertTrue(drivers.isEmpty())

        controller.onHostStarted()
        controller.start()
        assertEquals(1, drivers.size)
    }

    @Test
    fun `denied permission stays blocked until an explicit granted retry`() {
        val controller = controller()
        controller.showPermissionRequired()
        assertEquals(QlzEvaluationIssue.PERMISSION_REQUIRED, controller.uiState.value.issue)
        assertTrue(drivers.isEmpty())

        controller.onHostStopped()
        controller.onHostStarted()
        assertTrue(drivers.isEmpty())

        controller.start()
        callbacks.single()(QlzEvaluationDriverEvent.Authorized)
        assertEquals(QlzEvaluationStage.SCANNING, controller.uiState.value.stage)
        controller.cancel()
        assertEquals(1, releases)
        verify(exactly = 1) { drivers.single().close() }
    }

    @Test
    fun `disabled location service is rechecked before creating a session on retry`() {
        val controller = controller()
        every { client.createEvaluationSession(any(), any(), any(), any()) } returns
            QlzEvaluationSessionCreation.Blocked(
                QlzEvaluationIssue.LOCATION_SERVICE_DISABLED,
                QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT,
            )
        controller.start()
        assertEquals(QlzEvaluationIssue.LOCATION_SERVICE_DISABLED, controller.uiState.value.issue)
        assertTrue(drivers.isEmpty())

        // Restore the factory's ready response, as after the user enables location services.
        controller()
        controller.start()
        callbacks.single()(QlzEvaluationDriverEvent.Authorized)
        assertEquals(QlzEvaluationStage.SCANNING, controller.uiState.value.stage)
        verify(exactly = 2) { client.createEvaluationSession(any(), any(), any(), any()) }
        controller.close()
        assertEquals(1, releases)
    }
}
