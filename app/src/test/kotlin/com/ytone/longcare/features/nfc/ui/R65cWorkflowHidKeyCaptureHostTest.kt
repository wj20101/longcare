package com.ytone.longcare.features.nfc.ui

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65cWorkflowHidKeyCaptureHostTest {

    @Test
    fun `maps digit action down into workflow HID event`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 10L,
        )

        val result = toR65cWorkflowHidCapturedKeyEventIfRelevant(native)

        assertEquals(KeyEvent.KEYCODE_1, result?.keyCode)
        assertEquals("1", result?.displayChar)
        assertEquals(10L, result?.eventTimeMillis)
    }

    @Test
    fun `maps enter into workflow HID newline sentinel`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_ENTER,
            unicodeChar = '\n'.code,
            eventTime = 20L,
        )

        val result = toR65cWorkflowHidCapturedKeyEventIfRelevant(native)

        assertEquals("\\n", result?.displayChar)
    }

    @Test
    fun `ignores non action down event`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 30L,
        )

        assertNull(toR65cWorkflowHidCapturedKeyEventIfRelevant(native))
    }

    @Test
    fun `ignores back and volume keys`() {
        val back = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 40L)
        val volume = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0, 50L)

        assertNull(toR65cWorkflowHidCapturedKeyEventIfRelevant(back))
        assertNull(toR65cWorkflowHidCapturedKeyEventIfRelevant(volume))
    }

    private fun mockKeyEvent(
        action: Int,
        keyCode: Int,
        unicodeChar: Int,
        eventTime: Long,
    ): KeyEvent {
        return mockk {
            every { this@mockk.action } returns action
            every { this@mockk.keyCode } returns keyCode
            every { this@mockk.unicodeChar } returns unicodeChar
            every { this@mockk.eventTime } returns eventTime
        }
    }
}
