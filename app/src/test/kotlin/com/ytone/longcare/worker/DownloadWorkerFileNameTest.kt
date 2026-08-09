package com.ytone.longcare.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkerFileNameTest {
    @Test
    fun `file name cannot escape download directory`() {
        val result = sanitizeApkFileName("../../outside app")

        assertFalse(result.contains('/'))
        assertFalse(result.contains(".."))
        assertTrue(result.endsWith(".apk"))
    }

    @Test
    fun `valid apk file name is preserved`() {
        assertEquals("longcare_4.2.0.apk", sanitizeApkFileName("longcare_4.2.0.apk"))
    }
}
