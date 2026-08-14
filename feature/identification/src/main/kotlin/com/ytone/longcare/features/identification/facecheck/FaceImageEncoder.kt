package com.ytone.longcare.features.identification.facecheck

import android.graphics.Bitmap
import android.util.Base64
import com.ytone.longcare.common.image.ImageProcessingPolicies
import com.ytone.longcare.common.image.UnifiedJpegEncoder
import com.ytone.longcare.core.common.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class EncodedFaceImage(
    val base64: String,
    val byteCount: Int,
    val widthPx: Int,
    val heightPx: Int,
)

/** Encodes a detected face using the single app-wide 500 KiB API policy. */
class FaceImageEncoder @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun encode(bitmap: Bitmap): EncodedFaceImage =
        withContext(ioDispatcher) {
            encodeOnWorker(bitmap)
        }

    private suspend fun encodeOnWorker(source: Bitmap): EncodedFaceImage {
        require(!source.isRecycled) { "人脸图片已失效" }
        var candidate = source
        var ownsCandidate = false
        var lastError: IOException? = null

        try {
            repeat(MAX_SCALE_ATTEMPTS) { attempt ->
                currentCoroutineContext().ensureActive()
                try {
                    val encoded = UnifiedJpegEncoder.encode(
                        bitmap = candidate,
                        policy = ImageProcessingPolicies.FACE_COMPARISON_API,
                    )
                    return EncodedFaceImage(
                        base64 = Base64.encodeToString(encoded.bytes, Base64.NO_WRAP),
                        byteCount = encoded.bytes.size,
                        widthPx = candidate.width,
                        heightPx = candidate.height,
                    )
                } catch (error: IOException) {
                    lastError = error
                }

                if (attempt == MAX_SCALE_ATTEMPTS - 1) {
                    throw requireNotNull(lastError)
                }

                val shortEdge = minOf(candidate.width, candidate.height)
                if (shortEdge <= MIN_IMAGE_EDGE_PX) {
                    throw requireNotNull(lastError)
                }
                val scale = maxOf(
                    SCALE_FACTOR,
                    MIN_IMAGE_EDGE_PX.toFloat() / shortEdge.toFloat(),
                )
                val nextWidth = (candidate.width * scale).toInt()
                val nextHeight = (candidate.height * scale).toInt()
                if (nextWidth >= candidate.width || nextHeight >= candidate.height) {
                    throw requireNotNull(lastError)
                }

                val scaled = Bitmap.createScaledBitmap(
                    candidate,
                    nextWidth,
                    nextHeight,
                    true,
                )
                if (ownsCandidate) {
                    candidate.recycle()
                }
                candidate = scaled
                ownsCandidate = true
            }

            throw lastError ?: IOException("人脸图片压缩失败")
        } finally {
            if (ownsCandidate && !candidate.isRecycled) {
                candidate.recycle()
            }
        }
    }

    private companion object {
        const val MAX_SCALE_ATTEMPTS = 8
        const val SCALE_FACTOR = 0.8f
        const val MIN_IMAGE_EDGE_PX = 160
    }
}
