package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.os.Build
import android.view.View.MeasureSpec
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.target.ImageViewTarget
import coil3.transition.CrossfadeTransition
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.feature.photoupload.R
import com.ytone.longcare.features.photoupload.vm.CaptureStamp
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.WatermarkData
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatermarkJpegTest {
    private val cameraPage = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    @Test fun logoOrientationAndEveryTimeAndCoordinateGlyphSurviveFinalJpegEncoding() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val stamp = CaptureStamp("2026-11-06 11:11:12", LocationResult(30.987654321098765, 120.12345678901234, "test"))
        val coordinates = "120.12345678901234,30.987654321098766"
        val data = WatermarkData("服务前", "测试参保人员", "测试护理员", "测试地址一号楼二单元三层东侧房间".repeat(2))
        for (scale in listOf(1f, 1.4f, 2f)) {
            val config = Configuration(base.resources.configuration).apply { fontScale = scale; densityDpi = 320 }
            val context = base.createConfigurationContext(config)
            val reference = inflateWatermark(context)
            bindCameraWatermark(reference, data, stamp.time, coordinates)
            val root = reference.root
            root.measure(MeasureSpec.makeMeasureSpec(root.layoutParams.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val timeRect = Rect()
            val coordinateRect = Rect()
            reference.captureTimeTextView.getDrawingRect(timeRect)
            reference.coordinatesTextView.getDrawingRect(coordinateRect)
            root.offsetDescendantRectToMyCoords(reference.captureTimeTextView, timeRect)
            root.offsetDescendantRectToMyCoords(reference.coordinatesTextView, coordinateRect)
            assertTrue(timeRect.bottom <= reference.addressRow.top && reference.addressRow.bottom <= coordinateRect.top)
            assertTrue(coordinateRect.bottom <= root.height)

            val preview = reference.logoImageView
            val logoImage = requireNotNull(context.getDrawable(R.drawable.app_watermark_image)).asImage()
            CrossfadeTransition.Factory().create(
                ImageViewTarget(preview),
                SuccessResult(logoImage, ImageRequest.Builder(context).build(), DataSource.NETWORK),
            ).transition()
            (preview.drawable as Animatable).stop()
            assertNull(preview.drawable.constantState)
            val expectedLogo = requireNotNull(viewToBitmapSafe(preview))
            val loadedLogo = preview.drawable
            if (scale == 1.4f) preview.setImageDrawable(null)
            val width = if (scale == 1f) 1200 else 720
            val height = maxOf(if (scale == 1f) 800 else 1024, root.height + 56)
            val front = scale == 2f
            val pipeline = UnifiedImagePipeline(context, Dispatchers.IO)
            var delivered: File? = null
            takePhoto(context, data, preview, isFrontCamera = front, lifecycle = cameraPage.lifecycle, captureImage = { input, prepareWatermark ->
                // This logo finishes loading while the shutter is waiting for a location.
                if (scale == 1.4f) preview.setImageDrawable(loadedLogo)
                assertTrue(prepareWatermark())
                // CameraX may complete after the preview has changed.
                preview.setImageDrawable(null)
                writeCameraImage(input, width, height)
                stamp
            }, processCapturedImage = { request ->
                pipeline.processWatermarkedCapture(request)
            }) { image ->
                assertNull(delivered)
                delivered = image
                true
            }
            val photo = requireNotNull(delivered)
            assertTrue(photo.isFile)
            try {
                val jpeg = BitmapFactory.decodeFile(photo.absolutePath)
                val top = jpeg.height - 28 - root.height
                assertLogoInPhoto(expectedLogo, jpeg, 26 + preview.left, top + preview.top)
                val left = jpeg.getPixel(40, 40)
                val right = jpeg.getPixel(width - 40, 40)
                val (red, blue) = if (front) right to left else left to right
                assertTrue(Color.red(red) > 220 && Color.blue(red) < 35)
                assertTrue(Color.blue(blue) > 220 && Color.red(blue) < 35)
                assertRenderedText(reference.captureTimeTextView, stamp.time, jpeg, 26 + timeRect.left, top + timeRect.top)
                assertRenderedText(reference.coordinatesTextView, coordinates, jpeg, 26 + coordinateRect.left, top + coordinateRect.top)
                val folder = System.getProperty("watermark.verification.dir")?.let(::File)
                    ?: context.getExternalFilesDir("watermark-verification")
                if (folder != null) {
                    folder.mkdirs()
                    photo.copyTo(File(folder, "watermark-api${Build.VERSION.SDK_INT}-font${scale}.jpg"), overwrite = true)
                }
                jpeg.recycle()
            } finally {
                expectedLogo.recycle()
                photo.delete()
            }
        }
    }

    @Test fun cancellationDuringJpegEncodingDiscardsOnlyTheUndeliveredImage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pipeline = UnifiedImagePipeline(context, Dispatchers.IO)
        lateinit var published: File
        val job = launch {
            val caller = currentCoroutineContext()
            takePhoto(context, WatermarkData("服务后", "", "", ""), null, false, cameraPage.lifecycle,
                captureImage = { file, prepareWatermark ->
                    assertTrue(prepareWatermark())
                    writeCameraImage(file, 480, 320)
                    CaptureStamp("2026-11-06 11:11:12", LocationResult(30.0, 120.0, "test"))
                }, processCapturedImage = { request ->
                    pipeline.processWatermarkedCapture(request).also { file ->
                        published = file
                        caller.cancel()
                    }
                }) { error("Cancelled camera returned its result") }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(published.exists())
    }

    @Test fun oversizedWatermarkKeepsBothCornersInsideThePhoto() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val input = File.createTempFile("small-capture-", ".jpg", context.cacheDir)
        writeCameraImage(input, 160, 120)
        val watermark = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        watermark.eraseColor(Color.RED)
        Canvas(watermark).drawRect(192f, 112f, 240f, 160f, Paint().apply { color = Color.BLUE })
        val pipeline = UnifiedImagePipeline(context, Dispatchers.IO)
        val photo = pipeline.processWatermarkedCapture(WatermarkedCaptureRequest(input, watermark, 20f, 10f, false))
        try {
            val jpeg = BitmapFactory.decodeFile(photo.path)
            val topLeft = jpeg.getPixel(25, 35)
            val bottomRight = jpeg.getPixel(133, 103)
            assertTrue(Color.red(topLeft) > 220 && Color.blue(topLeft) < 40)
            assertTrue(Color.blue(bottomRight) > 220 && Color.red(bottomRight) < 40)
            jpeg.recycle()
        } finally { photo.delete() }
    }

    private fun writeCameraImage(input: File, width: Int, height: Int) {
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        Canvas(source).apply {
            drawRect(16f, 16f, 64f, 64f, Paint().apply { color = Color.RED })
            drawRect(width - 64f, 16f, width - 16f, 64f, Paint().apply { color = Color.BLUE })
        }
        input.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        source.recycle()
    }

    private fun assertLogoInPhoto(logo: Bitmap, photo: Bitmap, left: Int, top: Int) {
        var dark = 0
        var matched = 0
        for (y in 0 until logo.height) for (x in 0 until logo.width) {
            val pixel = logo.getPixel(x, y)
            if (Color.alpha(pixel) > 220 && Color.red(pixel) < 120) {
                dark++
                if (Color.red(photo.getPixel(left + x, top + y)) < 180) matched++
            }
        }
        assertTrue("Missing preview logo ($matched/$dark pixels)", dark > 50 && matched >= dark * 0.9)
    }

    /** Compare each visible character's ink against its pixels in the decoded, compressed photo. */
    private fun assertRenderedText(view: TextView, expected: String, jpeg: Bitmap, left: Int, top: Int) {
        val layout = view.layout
        val text = layout.text.toString()
        assertEquals(expected, text.replace("\u200B", ""))
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue(layout.height <= view.height)
        val mask = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(mask))
        for (i in text.indices.filterNot { text[it].isWhitespace() || text[it] == '\u200B' }) {
            val line = layout.getLineForOffset(i)
            val start = floor(layout.getPrimaryHorizontal(i)).toInt().coerceAtLeast(0)
            val endX = if (i + 1 == layout.getLineEnd(line)) layout.getLineRight(line) else layout.getPrimaryHorizontal(i + 1)
            val end = ceil(endX).toInt().coerceAtMost(view.width)
            var ink = 0
            var matched = 0
            for (x in start until end) {
                for (y in layout.getLineTop(line) until layout.getLineBottom(line)) {
                    if (Color.alpha(mask.getPixel(x, y)) >= 128) {
                        ink++
                        if (Color.red(jpeg.getPixel(left + x, top + y)) < 200) matched++
                    }
                }
            }
            assertTrue("Missing '${text[i]}' at $i in $expected ($matched/$ink pixels)", ink > 0 && matched >= ink * 0.90)
        }
        mask.recycle()
    }
}
