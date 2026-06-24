package com.ytone.longcare.features.nfctest.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class NfcTestScreenCopyActionTest {

    @Test
    fun copy_action_copies_uid_to_clipboard_and_requests_refocus() = runTest {
        var copiedText: String? = null
        var copiedCount = 0
        var refocusCount = 0

        val result = copyNormalizedUidAndRefocus(
            uid = "AB12",
            writeClipboardText = { text -> copiedText = text },
            onCopied = { copiedCount += 1 },
            onRequestRefocus = { refocusCount += 1 },
        )

        assertTrue(result)
        assertEquals("AB12", copiedText)
        assertEquals(1, copiedCount)
        assertEquals(1, refocusCount)
    }

    @Test
    fun copy_action_ignores_blank_uid() = runTest {
        var copiedText: String? = null
        var copiedCount = 0
        var refocusCount = 0

        val result = copyNormalizedUidAndRefocus(
            uid = "   ",
            writeClipboardText = { text -> copiedText = text },
            onCopied = { copiedCount += 1 },
            onRequestRefocus = { refocusCount += 1 },
        )

        assertFalse(result)
        assertEquals(null, copiedText)
        assertEquals(0, copiedCount)
        assertEquals(0, refocusCount)
    }
}
