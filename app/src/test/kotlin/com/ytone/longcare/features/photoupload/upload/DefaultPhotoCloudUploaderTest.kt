package com.ytone.longcare.features.photoupload.upload

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.common.image.ManagedImageFileStore
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.model.CosUploadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultPhotoCloudUploaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cosRepository = mockk<CosRepository>()
    private val managedImageFileStore = mockk<ManagedImageFileStore>()
    private val sourceFile = File(context.cacheDir, "upload_test.jpg").apply { writeText("image") }
    private val uploader =
        DefaultPhotoCloudUploader(
            context = context,
            cosRepository = cosRepository,
            managedImageFileStore = managedImageFileStore,
        )
    private val sourceUri = Uri.fromFile(sourceFile)

    init {
        every { managedImageFileStore.requireCurrentUserFile(sourceUri) } returns sourceFile
    }

    @Test
    fun `successful upload exposes validated key without resolving a file url`() =
        runTest {
            coEvery { cosRepository.uploadFile(any()) } returns
                CosUploadResult(
                    success = true,
                    key = "customer/photo.jpg",
                )

            val result = uploader.upload(sourceUri)

            assertThat(result.key).isEqualTo("customer/photo.jpg")
        }

    @Test
    fun `success without a usable key is rejected at the shared boundary`() =
        runTest {
            coEvery { cosRepository.uploadFile(any()) } returns
                CosUploadResult(
                    success = true,
                    key = " ",
                )

            val failure = runCatching { uploader.upload(sourceUri) }.exceptionOrNull()

            assertThat(failure).isInstanceOf(PhotoCloudUploadException::class.java)
        }

    @Test
    fun `expired user file is rejected before COS receives an upload`() = runTest {
        every { managedImageFileStore.requireCurrentUserFile(sourceUri) } throws
            IllegalStateException("expired user storage")

        val failure = runCatching { uploader.upload(sourceUri) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        coVerify(exactly = 0) { cosRepository.uploadFile(any()) }
    }
}
