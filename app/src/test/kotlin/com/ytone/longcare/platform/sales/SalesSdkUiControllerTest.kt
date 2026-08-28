package com.ytone.longcare.platform.sales

import android.app.Activity
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesSdkUiControllerTest {
    @Test
    fun `invalid activity never opens vendor page`() {
        val client = mockk<QlzSdkClient>(relaxed = true)
        val activity =
            mockk<Activity> {
                every { isFinishing } returns true
                every { isDestroyed } returns false
            }
        val controller = SalesSdkUiController(client)

        assertEquals(
            SalesSdkOpenResult.InvalidActivity,
            controller.openEvaluation(activity, "token", {}),
        )
        verify(exactly = 0) { client.openByToken(any(), any(), any()) }
    }

    @Test
    fun `repeated click is ignored until terminal event then retry can open`() {
        val callback = slot<(QlzSdkEvent) -> Unit>()
        val client =
            mockk<QlzSdkClient> {
                every { openByToken(any(), any(), capture(callback)) } just runs
            }
        val activity = validActivity()
        val controller = SalesSdkUiController(client)
        val observedEvents = mutableListOf<QlzSdkEvent>()

        assertEquals(
            SalesSdkOpenResult.Opened,
            controller.openEvaluation(activity, "token-1", observedEvents::add),
        )
        assertEquals(
            SalesSdkOpenResult.AlreadyOpen,
            controller.openEvaluation(activity, "token-1", observedEvents::add),
        )

        callback.captured(QlzSdkEvent.Cancelled)

        assertEquals(
            SalesSdkOpenResult.Opened,
            controller.openEvaluation(activity, "token-2", observedEvents::add),
        )
        assertEquals(listOf(QlzSdkEvent.Cancelled), observedEvents)
        verify(exactly = 2) { client.openByToken(activity, any(), any()) }
    }

    @Test
    fun `page invocation exception releases guard for retry`() {
        var attempts = 0
        val client =
            mockk<QlzSdkClient> {
                every { openByToken(any(), any(), any()) } answers {
                    attempts += 1
                    if (attempts == 1) error("vendor page unavailable")
                }
            }
        val activity = validActivity()
        val controller = SalesSdkUiController(client)

        assertEquals(
            SalesSdkOpenResult.Failed,
            controller.openEvaluation(activity, "token", {}),
        )
        assertEquals(
            SalesSdkOpenResult.Opened,
            controller.openEvaluation(activity, "token", {}),
        )
        assertEquals(2, attempts)
    }

    private fun validActivity(): Activity =
        mockk {
            every { isFinishing } returns false
            every { isDestroyed } returns false
        }
}
