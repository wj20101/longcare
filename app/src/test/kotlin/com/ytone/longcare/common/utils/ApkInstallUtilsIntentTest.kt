package com.ytone.longcare.common.utils

import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApkInstallUtilsIntentTest {

    @Test
    fun `buildInstallIntent uses view action and grants apk uri access`() {
        val uri = Uri.parse("content://com.ytone.longcare.fileprovider/update.apk")

        val intent = ApkInstallUtils.buildInstallIntent(uri, "update.apk")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertNotNull(intent.clipData)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(true, intent.getBooleanExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false))
    }
}
