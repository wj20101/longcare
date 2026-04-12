package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.features.nfc.ui.R65cWorkflowHidCapturedKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R65cWorkflowHidSessionCollectorTest {

    @Test
    fun `appends regular characters into one pending session`() {
        val collector = R65cWorkflowHidSessionCollector()

        collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(29, 'A'.code, "A", 1L))
        val result = collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(30, 'B'.code, "B", 2L))
        assertTrue(collector.hasPendingInput())
        val drained = collector.drainPending()

        assertNull(result)
        assertEquals("AB", drained)
        assertFalse(collector.hasPendingInput())
    }

    @Test
    fun `enter completes current session`() {
        val collector = R65cWorkflowHidSessionCollector()
        collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(29, 'A'.code, "A", 1L))

        val result = collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(66, '\n'.code, "\\n", 2L))

        assertEquals("A", result)
        assertFalse(collector.hasPendingInput())
    }

    @Test
    fun `drainPending returns buffered text and clears it`() {
        val collector = R65cWorkflowHidSessionCollector()
        collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(29, 'A'.code, "A", 1L))
        collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(30, 'B'.code, "B", 2L))

        val drained = collector.drainPending()

        assertEquals("AB", drained)
        assertFalse(collector.hasPendingInput())
    }

    @Test
    fun `empty display char does not create a session`() {
        val collector = R65cWorkflowHidSessionCollector()

        val result = collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(0, 0, "", 1L))

        assertNull(result)
        assertFalse(collector.hasPendingInput())
    }
}
