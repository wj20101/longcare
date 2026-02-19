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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object FaceCaptureStorageDelegate {

    suspend fun saveFaceImageToFiles(
        context: Context,
        bitmap: Bitmap,
        ioDispatcher: CoroutineDispatcher
    ): String? = withContext(ioDispatcher) {
        try {
            val faceCaptureDir = File(context.filesDir, "face_capture")
            if (!faceCaptureDir.exists()) {
                faceCaptureDir.mkdirs()
            }

            val timestamp =
                SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val file = File(faceCaptureDir, "face_$timestamp.jpg")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            logI("Face image saved successfully: ${file.absolutePath}", tag = "FaceCaptureViewModel")
            file.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
