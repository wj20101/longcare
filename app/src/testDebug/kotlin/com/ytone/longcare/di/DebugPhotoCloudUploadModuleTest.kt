package com.ytone.longcare.di

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.UploadedPhoto
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugPhotoCloudUploadModuleTest {
    @Test
    fun `explicit mock mode selects deterministic local uploader without calling real uploader`() =
        runTest {
            val realUploader = FailingRecordingUploader()
            val fakeUploader = DebugPhotoCloudUploader()
            val selected =
                selectDebugPhotoCloudUploader(
                    useMockData = true,
                    realUploader = realUploader,
                    fakeUploader = fakeUploader,
                )
            val uri = Uri.parse("content://test-owned/photos/sample")

            val first = selected.upload(uri, folderType = CosConstantsForTest)
            val second = selected.upload(uri, folderType = CosConstantsForTest)

            assertThat(selected).isSameInstanceAs(fakeUploader)
            assertThat(first).isEqualTo(second)
            assertThat(first.key).startsWith("mock-only/not-for-production/")
            assertThat(realUploader.uploadCalls).isEqualTo(0)
        }

    @Test
    fun `real debug mode selects production uploader boundary`() {
        val realUploader = RecordingUploader()
        val fakeUploader = RecordingUploader()

        val selected =
            selectDebugPhotoCloudUploader(
                useMockData = false,
                realUploader = realUploader,
                fakeUploader = fakeUploader,
            )

        assertThat(selected).isSameInstanceAs(realUploader)
    }

    private class RecordingUploader : PhotoCloudUploader {
        override suspend fun upload(uri: Uri, folderType: Int): UploadedPhoto =
            UploadedPhoto("unused")
    }

    private class FailingRecordingUploader : PhotoCloudUploader {
        var uploadCalls: Int = 0
            private set

        override suspend fun upload(uri: Uri, folderType: Int): UploadedPhoto {
            uploadCalls += 1
            error("Real COS boundary must not run in explicit mock mode")
        }
    }

    private companion object {
        const val CosConstantsForTest = 7
    }
}
