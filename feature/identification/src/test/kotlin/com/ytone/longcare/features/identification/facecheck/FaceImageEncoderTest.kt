package com.ytone.longcare.features.identification.facecheck

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.common.image.ImageProcessingPolicies
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceImageEncoderTest {
    @Test
    fun `encoded face is raw jpeg base64 within api limit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val encoder = FaceImageEncoder(dispatcher)
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(210, 170, 140))
        }

        try {
            val result = encoder.encode(bitmap)
            val decoded = Base64.decode(result.base64, Base64.NO_WRAP)

            assertThat(result.base64).doesNotContain("\n")
            assertThat(result.base64).doesNotContain("data:image")
            assertThat(result.byteCount).isEqualTo(decoded.size)
            assertThat(result.widthPx).isEqualTo(640)
            assertThat(result.heightPx).isEqualTo(640)
            assertThat(decoded.size.toLong())
                .isAtMost(ImageProcessingPolicies.FACE_COMPARISON_API.maxOutputBytes)
            assertThat(decoded[0].toInt() and 0xFF).isEqualTo(0xFF)
            assertThat(decoded[1].toInt() and 0xFF).isEqualTo(0xD8)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `large face is downscaled without recycling caller bitmap`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val encoder = FaceImageEncoder(dispatcher)
        val width = 1_200
        val height = 1_200
        val pixels = IntArray(width * height) { index ->
            var value = index * 1_103_515_245 + 12_345
            value = value xor (value ushr 16)
            Color.rgb(value and 0xFF, (value ushr 8) and 0xFF, (value ushr 16) and 0xFF)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }

        try {
            val result = encoder.encode(bitmap)

            assertThat(result.byteCount.toLong())
                .isAtMost(ImageProcessingPolicies.FACE_COMPARISON_API.maxOutputBytes)
            assertThat(result.widthPx).isAtMost(width)
            assertThat(result.heightPx).isAtMost(height)
            assertThat(bitmap.isRecycled).isFalse()
        } finally {
            bitmap.recycle()
        }
    }
}
