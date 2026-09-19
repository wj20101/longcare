package com.ytone.longcare.presentation.validation.nfc

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65CHidInputCaptureTest {

    @Test
    fun `action down character is captured`() {
        val event =
            mockKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_1,
                unicodeChar = '1'.code,
            )

        val captured = event.toR65CCapturedKeyOrNull()

        assertEquals(KeyEvent.KEYCODE_1, captured?.keyCode)
        assertEquals('1'.code, captured?.unicodeChar)
    }

    @Test
    fun `action up and system keys are ignored`() {
        val actionUp = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_1, '1'.code)
        val back = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        val volume = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0)

        assertNull(actionUp.toR65CCapturedKeyOrNull())
        assertNull(back.toR65CCapturedKeyOrNull())
        assertNull(volume.toR65CCapturedKeyOrNull())
    }

    @Test
    fun `enter terminator is captured even without unicode value`() {
        val enter = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0)

        val captured = enter.toR65CCapturedKeyOrNull()

        assertEquals(KeyEvent.KEYCODE_ENTER, captured?.keyCode)
    }

    private fun mockKeyEvent(
        action: Int,
        keyCode: Int,
        unicodeChar: Int,
    ): KeyEvent =
        mockk {
            every { this@mockk.action } returns action
            every { this@mockk.keyCode } returns keyCode
            every { this@mockk.unicodeChar } returns unicodeChar
        }
}
