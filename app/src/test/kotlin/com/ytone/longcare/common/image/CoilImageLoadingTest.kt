package com.ytone.longcare.common.image

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.di.ImageLoadingModule
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoilImageLoadingTest {
    @get:Rule val temporary = TemporaryFolder()
    private val loaders = mutableListOf<ImageLoader>()
    private val context by lazy {
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getCacheDir(): File = temporary.root
        }
    }

    private fun productionLoader(): ImageLoader {
        val config = mockk<RuntimeConfigProvider> { every { isDebug } returns false }
        return ImageLoadingModule.provideImageLoader(context, config).also(loaders::add)
    }

    @After fun releaseLoaders() {
        loaders.forEach { it.shutdown(); it.diskCache?.shutdown() }
    }

    @Test fun `local image decodes then hits the configured memory cache`() = runBlocking {
        val file = temporary.newFile("image.png")
        val bitmap = createBitmap(32, 32)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val loader = productionLoader()
        val request = ImageRequest.Builder(context).data(file).size(32, 32).allowHardware(false).build()
        val first = withTimeout(10_000) { loader.execute(request) }
        assertTrue(first.toString(), first is SuccessResult)
        first as SuccessResult
        assertEquals(32, first.image.width)
        assertEquals(32, first.image.height)
        assertNotNull(first.memoryCacheKey)
        val second = withTimeout(10_000) { loader.execute(request) } as SuccessResult
        assertEquals(DataSource.MEMORY_CACHE, second.dataSource)
        assertEquals(first.memoryCacheKey, second.memoryCacheKey)
    }

    @Test fun `configured disk cache commits reads aborts and removes entries`() {
        val cache = requireNotNull(productionLoader().diskCache)
        val editor = requireNotNull(cache.openEditor("image"))
        cache.fileSystem.write(editor.metadata) { writeUtf8("image/png") }
        cache.fileSystem.write(editor.data) { writeUtf8("fixture-image") }
        editor.commit()
        requireNotNull(cache.openSnapshot("image")).use { snapshot ->
            assertEquals("fixture-image", cache.fileSystem.read(snapshot.data) { readUtf8() })
            assertEquals("image/png", cache.fileSystem.read(snapshot.metadata) { readUtf8() })
        }
        requireNotNull(cache.openEditor("cancelled")).apply {
            cache.fileSystem.write(data) { writeUtf8("partial") }
            abort()
        }
        assertNull(cache.openSnapshot("cancelled"))
        assertTrue(cache.remove("image"))
        assertNull(cache.openSnapshot("image"))
    }

    @Test fun `cancelling a request releases its fetcher and reports cancellation once`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val cancellations = AtomicInteger()
        val loader = ImageLoader.Builder(context)
            .components {
                add(Fetcher.Factory<SlowImage> { _, _, _ ->
                    Fetcher {
                        started.complete(Unit)
                        try { awaitCancellation() } finally { released.complete(Unit) }
                    }
                })
            }
            .build().also(loaders::add)
        val request = ImageRequest.Builder(context).data(SlowImage()).size(32, 32)
            .listener(onCancel = { cancellations.incrementAndGet() }).build()
        val job = async(Dispatchers.Default) { loader.execute(request) }
        withTimeout(10_000) {
            started.await()
            job.cancelAndJoin()
            released.await()
        }
        assertTrue(job.isCancelled)
        assertEquals(1, cancellations.get())
    }

    private class SlowImage
}
