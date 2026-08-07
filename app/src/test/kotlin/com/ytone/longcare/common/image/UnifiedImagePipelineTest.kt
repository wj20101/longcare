package com.ytone.longcare.common.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UnifiedImagePipelineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `jpeg encoder rejects output larger than the configured limit`() {
        val bitmap = solidBitmap(width = 64, height = 64, color = Color.BLUE)
        val restrictivePolicy =
            ImageProcessingPolicy(
                targetShortEdgePx = null,
                initialJpegQuality = 90,
                minimumJpegQuality = 90,
                jpegQualityStep = 5,
                maxOutputBytes = 16,
            )

        val error =
            runCatching {
                UnifiedJpegEncoder.encode(bitmap = bitmap, policy = restrictivePolicy)
            }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        bitmap.recycle()
    }

    @Test
    fun `managed bitmap output is non-empty bounded and deletable`() =
        runTest {
            val pipeline =
                UnifiedImagePipeline(
                    context = context,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )
            val bitmap = solidBitmap(width = 640, height = 480, color = Color.GREEN)

            val output =
                pipeline.saveBitmap(
                    bitmap = bitmap,
                    purpose = ManagedImagePurpose.MANUAL_FACE_CAPTURE,
                    filePrefix = "pipeline_test",
                )

            assertThat(output.exists()).isTrue()
            assertThat(output.length()).isGreaterThan(0L)
            assertThat(output.length())
                .isAtMost(ImageProcessingPolicies.FACE_PHOTO.maxOutputBytes)
            assertThat(output.parentFile?.name).isEqualTo("face_captures")
            assertThat(pipeline.deleteManagedImage(Uri.fromFile(output))).isTrue()
            assertThat(output.exists()).isFalse()
            bitmap.recycle()
        }

    @Test
    fun `watermarked capture applies EXIF orientation compression and cleanup`() =
        runTest {
            val pipeline =
                UnifiedImagePipeline(
                    context = context,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )
            val sourceBitmap = solidBitmap(width = 1200, height = 800, color = Color.WHITE)
            val temporaryFile = File(context.cacheDir, "pipeline_source_${System.nanoTime()}.jpg")
            FileOutputStream(temporaryFile).use { output ->
                assertThat(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)).isTrue()
            }
            ExifInterface(temporaryFile.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_ROTATE_90.toString(),
                )
                saveAttributes()
            }
            val watermark = solidBitmap(width = 120, height = 60, color = Color.RED)

            val output =
                pipeline.processWatermarkedCapture(
                    WatermarkedCaptureRequest(
                        temporaryCaptureFile = temporaryFile,
                        watermarkBitmap = watermark,
                        watermarkStartPx = 20f,
                        watermarkBottomPx = 20f,
                        mirrorHorizontally = false,
                    )
                )
            val decoded = BitmapFactory.decodeFile(output.absolutePath)

            assertThat(decoded.width).isEqualTo(800)
            assertThat(decoded.height).isEqualTo(1200)
            assertThat(output.length())
                .isAtMost(ImageProcessingPolicies.WATERMARKED_PHOTO.maxOutputBytes)
            assertThat(temporaryFile.exists()).isFalse()
            assertThat(watermark.isRecycled).isTrue()
            val watermarkPixel = decoded.getPixel(70, decoded.height - 50)
            assertThat(Color.red(watermarkPixel)).isGreaterThan(Color.green(watermarkPixel))
            assertThat(Color.red(watermarkPixel)).isGreaterThan(Color.blue(watermarkPixel))

            decoded.recycle()
            sourceBitmap.recycle()
            pipeline.deleteManagedImage(Uri.fromFile(output))
        }

    @Test
    fun `watermarked capture failure still releases temporary artifacts`() =
        runTest {
            val pipeline =
                UnifiedImagePipeline(
                    context = context,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )
            val invalidCapture =
                File(context.cacheDir, "failed_capture_${System.nanoTime()}.jpg")
            val source = solidBitmap(width = 200, height = 200, color = Color.WHITE)
            FileOutputStream(invalidCapture).use { output ->
                assertThat(source.compress(Bitmap.CompressFormat.JPEG, 95, output)).isTrue()
            }
            val watermark = solidBitmap(width = 120, height = 60, color = Color.RED)
            val impossibleOutputPolicy =
                ImageProcessingPolicy(
                    targetShortEdgePx = null,
                    initialJpegQuality = 90,
                    minimumJpegQuality = 90,
                    jpegQualityStep = 5,
                    maxOutputBytes = 1,
                )

            val failure =
                runCatching {
                    pipeline.processWatermarkedCapture(
                        request =
                            WatermarkedCaptureRequest(
                                temporaryCaptureFile = invalidCapture,
                                watermarkBitmap = watermark,
                                watermarkStartPx = 0f,
                                watermarkBottomPx = 0f,
                                mirrorHorizontally = false,
                            ),
                        policy = impossibleOutputPolicy,
                    )
                }.exceptionOrNull()

            assertThat(failure).isInstanceOf(IOException::class.java)
            assertThat(invalidCapture.exists()).isFalse()
            assertThat(watermark.isRecycled).isTrue()
            source.recycle()
        }

    private fun solidBitmap(
        width: Int,
        height: Int,
        color: Int,
    ): Bitmap =
        createBitmap(width, height).apply { eraseColor(color) }
}
