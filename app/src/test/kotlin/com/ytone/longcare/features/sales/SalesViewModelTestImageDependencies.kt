package com.ytone.longcare.features.sales

import android.content.Context
import android.net.Uri
import com.ytone.longcare.common.image.TestManagedImageFileStore
import com.ytone.longcare.common.image.UnifiedImagePipeline
import java.io.File
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.UploadedPhoto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

internal object UnusedPhotoCloudUploader : PhotoCloudUploader {
    override suspend fun upload(
        uri: Uri,
        folderType: Int,
    ): UploadedPhoto = error("This test does not upload photos.")
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun testImagePipeline(context: Context): UnifiedImagePipeline =
    UnifiedImagePipeline(
        context = context,
        ioDispatcher = StandardTestDispatcher(),
        fileStore = TestManagedImageFileStore(
            File(
                requireNotNull(System.getProperty("java.io.tmpdir")),
                "longcare_sales_image_test_${System.nanoTime()}",
            ),
        ),
    )
