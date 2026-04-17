package com.ytone.longcare.features.nfctest.ui

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65CHidInputCaptureHostTest {

    @Test
    fun `host captures digit key on action down`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 20L,
        )

        val result = toR65CHidCapturedKeyEventIfRelevant(native)

        assertEquals(KeyEvent.KEYCODE_1, result?.keyCode)
        assertEquals('1'.code, result?.unicodeChar)
        assertEquals("1", result?.displayChar)
        assertEquals(20L, result?.eventTimeMillis)
    }

    @Test
    fun `host ignores action up back and volume keys`() {
        val keyUp = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_1, '1'.code, 30L)
        val back = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 40L)
        val volume = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0, 50L)

        assertNull(toR65CHidCapturedKeyEventIfRelevant(keyUp))
        assertNull(toR65CHidCapturedKeyEventIfRelevant(back))
        assertNull(toR65CHidCapturedKeyEventIfRelevant(volume))
    }

    @Test
    fun `host maps enter key to visible newline token`() {
        val enter = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_ENTER,
            unicodeChar = '\n'.code,
            eventTime = 60L,
        )

        val result = toR65CHidCapturedKeyEventIfRelevant(enter)

        assertEquals("\\n", result?.displayChar)
        assertEquals('\n'.code, result?.unicodeChar)
    }

    @Test
    fun `host keeps non ignored zero unicode key with empty display char`() {
        val shift = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
            unicodeChar = 0,
            eventTime = 70L,
        )

        val result = toR65CHidCapturedKeyEventIfRelevant(shift)

        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, result?.keyCode)
        assertEquals(0, result?.unicodeChar)
        assertEquals("", result?.displayChar)
        assertEquals(70L, result?.eventTimeMillis)
    }

    private fun mockKeyEvent(
        action: Int,
        keyCode: Int,
        unicodeChar: Int,
        eventTime: Long,
    ): KeyEvent = mockk {
        every { this@mockk.action } returns action
        every { this@mockk.keyCode } returns keyCode
        every { this@mockk.unicodeChar } returns unicodeChar
        every { this@mockk.eventTime } returns eventTime
    }
}
