package com.ytone.longcare.features.shared

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.FileOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FaceVerificationPhotoProcessorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `processor reads unified pipeline output without recompressing`() = runTest {
        val source = temporaryFolder.newFile("face.jpg")
        val bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        FileOutputStream(source).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        val originalBytes = source.readBytes()
        val processor = FaceVerificationPhotoProcessor(StandardTestDispatcher(testScheduler))

        val processed = processor.process(source.absolutePath)

        assertEquals(12, processed.bitmap.width)
        assertEquals(8, processed.bitmap.height)
        assertArrayEquals(originalBytes, Base64.decode(processed.base64, Base64.NO_WRAP))
    }
}
