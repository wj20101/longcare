package com.ytone.longcare.features.shared

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.core.common.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ProcessedFacePhoto(
    val bitmap: Bitmap,
    val base64: String
)

enum class FacePhotoProcessingFailure {
    MISSING_FILE,
    INVALID_IMAGE,
    UNEXPECTED,
}

class FacePhotoProcessingException(
    val failure: FacePhotoProcessingFailure,
    cause: Throwable? = null,
) : Exception(cause)

class FaceVerificationPhotoProcessor @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun process(imagePath: String): ProcessedFacePhoto =
        withContext(ioDispatcher) {
            val imageFile = File(imagePath)
            try {
                currentCoroutineContext().ensureActive()
                if (!imageFile.isFile) {
                    throw FacePhotoProcessingException(FacePhotoProcessingFailure.MISSING_FILE)
                }

                // ManualFaceCaptureStorageDelegate has already compressed this through UnifiedImagePipeline.
                val imageBytes = imageFile.readBytes()
                currentCoroutineContext().ensureActive()
                val bitmap =
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        ?: throw FacePhotoProcessingException(FacePhotoProcessingFailure.INVALID_IMAGE)
                val sourcePhotoBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                ProcessedFacePhoto(
                    bitmap = bitmap,
                    base64 = sourcePhotoBase64,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                DiagnosticEventTracker.trackError(
                    category = "face_verification",
                    event = "shared_face_photo_process_exception",
                    description = "共享人脸照片处理异常",
                    throwable = exception,
                    extras = imageFile.diagnosticExtras(),
                )
                throw if (exception is FacePhotoProcessingException) {
                    exception
                } else {
                    FacePhotoProcessingException(
                        failure = FacePhotoProcessingFailure.UNEXPECTED,
                        cause = exception,
                    )
                }
            }
        }
}

private fun File.diagnosticExtras(): Map<String, Any?> =
    mapOf(
        "fileExists" to exists(),
        "fileSize" to takeIf { exists() }?.length(),
        "pathLength" to path.length,
    )
