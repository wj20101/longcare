package com.ytone.longcare.assistant

import android.graphics.Bitmap
import android.net.Uri
import com.ytone.longcare.common.image.ManagedImagePurpose
import com.ytone.longcare.common.image.UnifiedImagePipeline
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssistantPhotoCleanerTest {
    @Test fun `deletes discarded managed photos without touching current photos or unrelated files`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val pipeline = UnifiedImagePipeline(context, StandardTestDispatcher(testScheduler))
        val cleaner = AssistantPhotoCleaner(pipeline, this)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val old = pipeline.saveBitmap(bitmap, ManagedImagePurpose.WATERMARKED_PHOTO, "old")
        val current = pipeline.saveBitmap(bitmap, ManagedImagePurpose.MANUAL_FACE_CAPTURE, "current")
        val unrelated = File(context.filesDir, "unrelated-review-test.txt").apply { writeText("keep") }
        try {
            cleaner.discard(old.toURI().toString())
            cleaner.discard(unrelated.toURI().toString())
            cleaner.discard("content://untrusted/image")
            cleaner.discard("")
            advanceUntilIdle()
            assertFalse(old.exists())
            assertTrue(current.exists())
            assertTrue(unrelated.exists())
            cleaner.discard(current.toURI().toString())
            advanceUntilIdle()
            assertFalse(current.exists())
        } finally {
            pipeline.deleteManagedImage(Uri.fromFile(old))
            pipeline.deleteManagedImage(Uri.fromFile(current))
            unrelated.delete()
            bitmap.recycle()
        }
    }
}
