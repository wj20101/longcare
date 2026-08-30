package com.ytone.longcare.di

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugPhotoCloudUploaderDeviceTest {
    @Test
    fun fakeUploadIsDeterministicAndUnmistakablyNonProduction() =
        runBlocking {
            val uploader = DebugPhotoCloudUploader()
            val uri = Uri.parse("content://device-test-owned/photos/offline-sample")

            val first = uploader.upload(uri, folderType = 7)
            val second = uploader.upload(uri, folderType = 7)

            assertEquals(first, second)
            assertTrue(first.key.startsWith("mock-only/not-for-production/7/"))
        }
}
