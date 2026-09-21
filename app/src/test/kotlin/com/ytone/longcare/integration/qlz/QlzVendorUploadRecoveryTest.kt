package com.ytone.longcare.integration.qlz

import android.app.Activity
import android.app.Application
import com.evenmed.sdk.call.CheckCallIml
import com.evenmed.sdk.call.CheckIml
import com.evenmed.sdk.call.ConnectDeviceHelp
import com.falth.data.RecordInputData
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class QlzVendorUploadRecoveryTest {
    @Test
    fun `reauthorization keeps connector and exact vendor record and ignores old authorization callbacks`() {
        mockkStatic(CheckIml::class)
        val activity = mockk<Activity>(relaxed = true)
        val connector = mockk<ConnectDeviceHelp>(relaxed = true)
        val driver = QlzVendorEvaluationDriver(activity)
        // Supply only the hardware boundary; exercise the real driver and upload buffer.
        val connectorField = driver.javaClass.getDeclaredField("connector").apply { isAccessible = true }
        connectorField.set(driver, connector)
        val callbackField = driver.javaClass.getDeclaredField("uploadCallback").apply { isAccessible = true }
        val uploadCallback = callbackField.get(driver) as CheckCallIml.UpDataCallback
        val authorizations = mutableListOf<CheckCallIml.CheckUserCallback>()
        every { CheckIml.startCheck(activity, any<String>(), any<CheckCallIml.CheckUserCallback>()) } answers {
            authorizations += thirdArg<CheckCallIml.CheckUserCallback>()
        }
        val oldEvents = mutableListOf<QlzEvaluationDriverEvent>()
        val freshEvents = mutableListOf<QlzEvaluationDriverEvent>()
        val record = RecordInputData().apply { recordid = "same-record" }
        try {
            driver.authorize("first", oldEvents::add)
            authorizations[0].onSuccess(null)
            uploadCallback.onUpFail(record, 401, "ignored vendor text")
            driver.authorize("fresh", freshEvents::add)
            authorizations[0].onTokenOut()
            assertTrue(freshEvents.isEmpty())
            authorizations[1].onSuccess(null)
            authorizations[1].onTokenOut()
            authorizations[1].onSuccess(null)
            assertEquals(listOf(QlzEvaluationDriverEvent.Authorized), freshEvents)
            assertSame(connector, connectorField.get(driver))
            driver.retryUpload()
            verify(exactly = 1) { connector.sendData(refEq(record)) }
            assertEquals("same-record", record.recordid)
            verify(exactly = 0) { connector.onDestroy() }
            verify(exactly = 0) { connector.breakCheck() }
            driver.close()
            authorizations[1].onTokenOut()
            assertEquals(1, freshEvents.size)
        } finally {
            driver.close()
            unmockkStatic(CheckIml::class)
        }
    }
}
