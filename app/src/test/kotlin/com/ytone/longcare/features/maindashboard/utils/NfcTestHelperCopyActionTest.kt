package com.ytone.longcare.features.maindashboard.utils

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.common.utils.LogConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NfcTestHelperCopyActionTest {

    @Before
    fun setUp() {
        KLogger.init(LogConfig(enabled = false))
    }

    @Test
    fun copy_action_copies_tag_shows_success_and_dismisses() = runTest {
        var copiedText: String? = null
        var successCount = 0
        var failureCount = 0
        var dismissCount = 0

        val result = copyNfcTagIdAndDismiss(
            tagId = "AB12CD34",
            writeClipboardEntry = { text -> copiedText = text },
            onCopySuccess = { successCount += 1 },
            onCopyFailure = { failureCount += 1 },
            dismissDialog = { dismissCount += 1 }
        )

        assertTrue(result)
        assertEquals("AB12CD34", copiedText)
        assertEquals(1, successCount)
        assertEquals(0, failureCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun copy_action_shows_failure_and_still_dismisses_when_clipboard_write_throws() = runTest {
        var successCount = 0
        var failureCount = 0
        var dismissCount = 0

        val result = copyNfcTagIdAndDismiss(
            tagId = "AB12CD34",
            writeClipboardEntry = { throw IllegalStateException("clipboard unavailable") },
            onCopySuccess = { successCount += 1 },
            onCopyFailure = { failureCount += 1 },
            dismissDialog = { dismissCount += 1 }
        )

        assertFalse(result)
        assertEquals(0, successCount)
        assertEquals(1, failureCount)
        assertEquals(1, dismissCount)
    }
}
