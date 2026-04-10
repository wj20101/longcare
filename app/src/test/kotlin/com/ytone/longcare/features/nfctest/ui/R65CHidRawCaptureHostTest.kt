package com.ytone.longcare.features.nfctest.ui

import android.view.KeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65CHidRawCaptureHostTest {

    @Test
    fun `host ignores non listening state`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 10L,
        )

        val result = toR65CHidCapturedKeyEventIfRelevant(
            isListening = false,
            currentState = R65CHidRawCaptureState.Idle,
            keyEvent = native,
        )

        assertNull(result)
    }

    @Test
    fun `host captures digit key while armed`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 20L,
        )

        val result = toR65CHidCapturedKeyEventIfRelevant(
            isListening = true,
            currentState = R65CHidRawCaptureState.Armed,
            keyEvent = native,
        )

        assertEquals(KeyEvent.KEYCODE_1, result?.keyCode)
        assertEquals('1'.code, result?.unicodeChar)
        assertEquals("1", result?.displayChar)
        assertEquals(native.eventTime, result?.eventTimeMillis)
    }

    @Test
    fun `host ignores non action down event`() {
        val keyUp = mockKeyEvent(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 30L,
        )

        val result = toR65CHidCapturedKeyEventIfRelevant(
            isListening = true,
            currentState = R65CHidRawCaptureState.Armed,
            keyEvent = keyUp,
        )

        assertNull(result)
    }

    @Test
    fun `host ignores back and volume keys`() {
        val back = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_BACK,
            unicodeChar = 0,
            eventTime = 40L,
        )
        val volume = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_VOLUME_UP,
            unicodeChar = 0,
            eventTime = 50L,
        )

        assertNull(toR65CHidCapturedKeyEventIfRelevant(true, R65CHidRawCaptureState.Armed, back))
        assertNull(toR65CHidCapturedKeyEventIfRelevant(true, R65CHidRawCaptureState.Armed, volume))
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
