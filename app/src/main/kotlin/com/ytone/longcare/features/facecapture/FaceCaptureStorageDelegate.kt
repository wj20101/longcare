package com.ytone.longcare.features.facecapture

import android.content.Context
import android.graphics.Bitmap
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object FaceCaptureStorageDelegate {

    suspend fun saveFaceImageToFiles(
        context: Context,
        bitmap: Bitmap,
        ioDispatcher: CoroutineDispatcher
    ): String? = withContext(ioDispatcher) {
        var file: File? = null
        try {
            val faceCaptureDir = File(context.filesDir, "face_capture")
            if (!faceCaptureDir.exists()) {
                faceCaptureDir.mkdirs()
            }

            val timestamp =
                SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val targetFile = File(faceCaptureDir, "face_$timestamp.jpg")
            file = targetFile

            val compressed = FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (!compressed) {
                targetFile.delete()
                throw IOException("Face image compression failed.")
            }
            if (targetFile.length() <= 0L) {
                targetFile.delete()
                throw IOException("Face image file is empty.")
            }

            logI("Face image saved successfully: ${targetFile.absolutePath}", tag = "FaceCaptureViewModel")
            targetFile.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            file?.delete()
            logE("Failed to save face image", tag = "FaceCaptureViewModel", throwable = e)
            null
        }
    }

    fun getSavedFaceImages(context: Context): List<String> {
        return try {
            val faceCaptureDir = File(context.filesDir, "face_capture")
            if (faceCaptureDir.exists()) {
                faceCaptureDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".jpg")
                }?.map { it.absolutePath } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logE("Failed to get saved face images", tag = "FaceCaptureViewModel", throwable = e)
            emptyList()
        }
    }
}
