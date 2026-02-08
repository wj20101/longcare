package com.ytone.longcare.features.photoupload.utils

import android.app.ActivityManager
import android.content.Context
import com.ytone.longcare.di.ImageLoadingModule
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * 测试Coil缓存优化配置
 */
class CoilCacheOptimizationTest {

    private fun buildContext(
        availableMemoryBytes: Long = 5L * 1024 * 1024 * 1024,
        cacheDir: File = File(System.getProperty("java.io.tmpdir"), "coil-cache-test")
    ): Context {
        val context = mockk<Context>()
        val activityManager = mockk<ActivityManager>()

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        every { context.applicationContext } returns context
        every { context.cacheDir } returns cacheDir
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { activityManager.getMemoryInfo(any()) } answers {
            firstArg<ActivityManager.MemoryInfo>().apply {
                availMem = availableMemoryBytes
            }
            Unit
        }
        return context
    }

    @Test
    fun `test ImageLoader has memory cache configured`() {
        // Given
        val context = buildContext()
        
        // When
        val imageLoader = ImageLoadingModule.provideImageLoader(context)
        
        // Then
        assertNotNull("ImageLoader should not be null", imageLoader)
        assertNotNull("Memory cache should be configured", imageLoader.memoryCache)
        assertNotNull("Disk cache should be configured", imageLoader.diskCache)
    }
    
    @Test
    fun `test memory cache configuration`() {
        // Given
        val context = buildContext()
        
        // When
        val imageLoader = ImageLoadingModule.provideImageLoader(context)
        val memoryCache = imageLoader.memoryCache
        
        // Then
        assertNotNull("Memory cache should not be null", memoryCache)
        assertTrue("Memory cache max size should be greater than 0", memoryCache?.maxSize ?: 0 > 0)
    }
    
    @Test
    fun `test disk cache configuration`() {
        // Given
        val context = buildContext()
        
        // When
        val imageLoader = ImageLoadingModule.provideImageLoader(context)
        val diskCache = imageLoader.diskCache
        
        // Then
        assertNotNull("Disk cache should not be null", diskCache)
        assertTrue("Disk cache max size should be greater than 0", (diskCache?.maxSize ?: 0) > 0L)
    }
}
