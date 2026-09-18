package com.ytone.longcare.features.face.viewmodel

import android.graphics.Bitmap
import com.ytone.longcare.common.image.ManagedImagePurpose
import com.ytone.longcare.common.image.UnifiedImagePipeline
import javax.inject.Inject

class ManualFaceCaptureStorageDelegate @Inject constructor(
    private val imagePipeline: UnifiedImagePipeline,
) {
    suspend fun saveBitmapToFile(bitmap: Bitmap): String =
        imagePipeline
            .saveBitmap(
                bitmap = bitmap,
                purpose = ManagedImagePurpose.MANUAL_FACE_CAPTURE,
                filePrefix = "face_capture",
            ).absolutePath
}
