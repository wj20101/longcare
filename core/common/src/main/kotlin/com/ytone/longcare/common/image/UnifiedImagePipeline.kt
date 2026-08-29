package com.ytone.longcare.common.image

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.ytone.longcare.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val CAMERA_CAPTURE_PURPOSE = "camera_capture"

data class WatermarkedCaptureRequest(
    val temporaryCaptureFile: ManagedImageFile,
    val watermarkBitmap: Bitmap,
    val watermarkStartPx: Float,
    val watermarkBottomPx: Float,
    val mirrorHorizontally: Boolean,
)

/**
 * App-wide image output boundary: decode/orient, watermark, compress, persist and delete.
 */
@Singleton
class UnifiedImagePipeline @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val fileStore: ManagedImageFileStore,
) {
    fun createTemporaryCaptureFile(): ManagedImageFile =
        fileStore.createSessionFile(
            purpose = CAMERA_CAPTURE_PURPOSE,
            filePrefix = "camera_capture",
        )

    fun requireCurrentUserFile(uri: Uri): File = fileStore.requireCurrentUserFile(uri)

    suspend fun processWatermarkedCapture(
        request: WatermarkedCaptureRequest,
        policy: ImageProcessingPolicy = ImageProcessingPolicies.WATERMARKED_PHOTO,
    ): File =
        withContext(ioDispatcher) {
            withTimeout(processingTimeoutMillis()) {
                processWatermarkedCaptureOnWorker(request, policy)
            }
        }

    suspend fun saveBitmap(
        bitmap: Bitmap,
        purpose: ManagedImagePurpose,
        filePrefix: String,
        policy: ImageProcessingPolicy = ImageProcessingPolicies.FACE_PHOTO,
    ): File =
        withContext(ioDispatcher) {
            val target = createOutputFile(purpose = purpose, filePrefix = filePrefix)
            writeJpegAtomically(bitmap = bitmap, target = target, policy = policy)
        }

    suspend fun deleteManagedImage(uri: Uri): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                fileStore.deleteCurrentSessionFile(uri, managedImagePurposes)
            }.getOrDefault(false)
        }

    suspend fun deleteManagedImages(uris: Iterable<Uri>) {
        withContext(ioDispatcher) {
            uris.forEach { uri ->
                runCatching {
                    fileStore.deleteCurrentSessionFile(uri, managedImagePurposes)
                }
            }
        }
    }

    fun listManagedImages(purpose: ManagedImagePurpose): List<String> =
        runCatching {
            fileStore.listCurrentSessionFiles(purpose.storagePurpose)
                .filter { file -> file.extension.equals("jpg", ignoreCase = true) }
                .sortedByDescending(File::lastModified)
                .map(File::getAbsolutePath)
        }.getOrDefault(emptyList())

    private suspend fun processWatermarkedCaptureOnWorker(
        request: WatermarkedCaptureRequest,
        policy: ImageProcessingPolicy,
    ): File {
        var decodedBitmap: Bitmap? = null
        var orientedBitmap: Bitmap? = null
        var mirroredBitmap: Bitmap? = null
        var outputBitmap: Bitmap? = null

        try {
            fileStore.requireCurrent(request.temporaryCaptureFile)
            currentCoroutineContext().ensureActive()
            decodedBitmap = decodeSampled(request.temporaryCaptureFile.file, policy)
            orientedBitmap = applyExifOrientation(decodedBitmap, request.temporaryCaptureFile.file)
            if (orientedBitmap !== decodedBitmap) {
                decodedBitmap.recycleSafely()
                decodedBitmap = null
            }

            val source = requireNotNull(orientedBitmap)
            mirroredBitmap =
                if (request.mirrorHorizontally) {
                    transformBitmap(source) { matrix -> matrix.postScale(-1f, 1f) }
                } else {
                    source
                }
            if (mirroredBitmap !== source) {
                source.recycleSafely()
                orientedBitmap = null
            }

            currentCoroutineContext().ensureActive()
            outputBitmap =
                addWatermark(
                    source = requireNotNull(mirroredBitmap),
                    watermark = request.watermarkBitmap,
                    startPx = request.watermarkStartPx,
                    bottomPx = request.watermarkBottomPx,
                )
            if (outputBitmap !== mirroredBitmap) {
                mirroredBitmap.recycleSafely()
                mirroredBitmap = null
            }

            currentCoroutineContext().ensureActive()
            val target =
                createOutputFile(
                    purpose = ManagedImagePurpose.WATERMARKED_PHOTO,
                    filePrefix = "captured_image",
                )
            return writeJpegAtomically(
                bitmap = requireNotNull(outputBitmap),
                target = target,
                policy = policy,
            )
        } finally {
            decodedBitmap.recycleSafely()
            orientedBitmap.recycleSafely()
            mirroredBitmap.recycleSafely()
            outputBitmap.recycleSafely()
            request.watermarkBitmap.recycleSafely()
            fileStore.deleteOwned(request.temporaryCaptureFile)
        }
    }

    private fun decodeSampled(
        source: File,
        policy: ImageProcessingPolicy,
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Captured image bounds could not be decoded.")
        }

        val sampleSize =
            policy.targetShortEdgePx?.let { target ->
                calculateSampleSize(bounds.outWidth, bounds.outHeight, target)
            } ?: 1
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return BitmapFactory.decodeFile(source.absolutePath, options)
            ?: throw IOException("Captured image could not be decoded.")
    }

    private fun applyExifOrientation(
        bitmap: Bitmap,
        source: File,
    ): Bitmap {
        val orientation =
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        return when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                transformBitmap(bitmap) { it.postScale(-1f, 1f) }

            ExifInterface.ORIENTATION_ROTATE_180 ->
                transformBitmap(bitmap) { it.postRotate(180f) }

            ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                transformBitmap(bitmap) { it.postScale(1f, -1f) }

            ExifInterface.ORIENTATION_TRANSPOSE ->
                transformBitmap(bitmap) {
                    it.postRotate(90f)
                    it.postScale(-1f, 1f)
                }

            ExifInterface.ORIENTATION_ROTATE_90 ->
                transformBitmap(bitmap) { it.postRotate(90f) }

            ExifInterface.ORIENTATION_TRANSVERSE ->
                transformBitmap(bitmap) {
                    it.postRotate(270f)
                    it.postScale(-1f, 1f)
                }

            ExifInterface.ORIENTATION_ROTATE_270 ->
                transformBitmap(bitmap) { it.postRotate(270f) }

            else -> bitmap
        }
    }

    private fun transformBitmap(
        bitmap: Bitmap,
        configure: (Matrix) -> Unit,
    ): Bitmap {
        val matrix = Matrix().also(configure)
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }

    private fun addWatermark(
        source: Bitmap,
        watermark: Bitmap,
        startPx: Float,
        bottomPx: Float,
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawBitmap(source, 0f, 0f, null)
            drawBitmap(
                watermark,
                startPx.coerceAtLeast(0f),
                (source.height - watermark.height - bottomPx).coerceAtLeast(0f),
                null,
            )
        }
        return output
    }

    private fun writeJpegAtomically(
        bitmap: Bitmap,
        target: ManagedImageFile,
        policy: ImageProcessingPolicy,
    ): File {
        fileStore.requireCurrent(target)
        val encoded = UnifiedJpegEncoder.encode(bitmap = bitmap, policy = policy)
        fileStore.requireCurrent(target)
        val targetFile = target.file
        val parent = targetFile.parentFile ?: throw IOException("Image output directory is unavailable.")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Image output directory could not be created.")
        }
        val temporary = File.createTempFile(".${targetFile.nameWithoutExtension}_", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encoded.bytes)
                output.fd.sync()
            }
            fileStore.requireCurrent(target)
            if (temporary.length() <= 0L) {
                throw IOException("Image output file is empty.")
            }
            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Existing image output could not be replaced.")
            }
            if (!temporary.renameTo(targetFile)) {
                temporary.copyTo(target = targetFile, overwrite = true)
                if (!temporary.delete()) {
                    temporary.deleteOnExit()
                }
            }
            fileStore.requireCurrent(target)
            if (targetFile.length() <= 0L || targetFile.length() > policy.maxOutputBytes) {
                targetFile.delete()
                throw IOException("Image output failed size validation.")
            }
            return targetFile
        } catch (error: Throwable) {
            temporary.delete()
            fileStore.deleteOwned(target)
            throw error
        }
    }

    private fun createOutputFile(
        purpose: ManagedImagePurpose,
        filePrefix: String,
    ): ManagedImageFile =
        fileStore.createSessionFile(
            purpose = purpose.storagePurpose,
            filePrefix = filePrefix,
        )

    private fun processingTimeoutMillis(): Long {
        val memoryClass =
            context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 128
        return when {
            memoryClass >= 256 -> 15_000L
            memoryClass >= 128 -> 25_000L
            else -> 35_000L
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        targetShortEdgePx: Int,
    ): Int {
        var sampleSize = 1
        while (true) {
            val next = sampleSize * 2
            if (minOf(width / next, height / next) >= targetShortEdgePx) {
                sampleSize = next
            } else {
                return sampleSize
            }
        }
    }

    private fun Bitmap?.recycleSafely() {
        if (this != null && !isRecycled) recycle()
    }

    private companion object {
        val managedImagePurposes = ManagedImagePurpose.entries.mapTo(mutableSetOf()) { it.storagePurpose }
    }
}
