package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcTestScreenCopyActionTest {

    @Test
    fun copy_action_copies_uid_to_clipboard_and_requests_refocus() {
        val clipboardManager = mockk<ClipboardManager>()
        val copiedText = slot<AnnotatedString>()
        var copiedCount = 0
        var refocusCount = 0

        every { clipboardManager.setText(capture(copiedText)) } just runs

        val result = copyNormalizedUidAndRefocus(
            uid = "AB12",
            clipboardManager = clipboardManager,
            onCopied = { copiedCount += 1 },
            onRequestRefocus = { refocusCount += 1 },
        )

        assertTrue(result)
        assertEquals("AB12", copiedText.captured.text)
        assertEquals(1, copiedCount)
        assertEquals(1, refocusCount)
        verify(exactly = 1) { clipboardManager.setText(any()) }
    }

    @Test
    fun copy_action_ignores_blank_uid() {
        val clipboardManager = mockk<ClipboardManager>()
        var copiedCount = 0
        var refocusCount = 0

        val result = copyNormalizedUidAndRefocus(
            uid = "   ",
            clipboardManager = clipboardManager,
            onCopied = { copiedCount += 1 },
            onRequestRefocus = { refocusCount += 1 },
        )

        assertFalse(result)
        assertEquals(0, copiedCount)
        assertEquals(0, refocusCount)
        verify(exactly = 0) { clipboardManager.setText(any()) }
    }
}
