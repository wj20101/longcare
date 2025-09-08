package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.ytone.longcare.di.ImageLoadingModule
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*

/**
 * 测试Coil缓存优化配置
 */
class CoilCacheOptimizationTest {

    @Test
    fun `test ImageLoader has memory cache configured`() {
        // Given
        val context = mockk<Context>(relaxed = true)
        val imageLoadingModule = ImageLoadingModule
        
        // When
        val imageLoader = imageLoadingModule.provideImageLoader(context)
        
        // Then
        assertNotNull("ImageLoader should not be null", imageLoader)
        assertNotNull("Memory cache should be configured", imageLoader.memoryCache)
        assertNotNull("Disk cache should be configured", imageLoader.diskCache)
    }
    
    @Test
    fun `test memory cache configuration`() {
        // Given
        val context = mockk<Context>(relaxed = true)
        val imageLoadingModule = ImageLoadingModule
        
        // When
        val imageLoader = imageLoadingModule.provideImageLoader(context)
        val memoryCache = imageLoader.memoryCache
        
        // Then
        assertNotNull("Memory cache should not be null", memoryCache)
        assertTrue("Memory cache max size should be greater than 0", memoryCache?.maxSize ?: 0 > 0)
    }
    
    @Test
    fun `test disk cache configuration`() {
        // Given
        val context = mockk<Context>(relaxed = true)
        val imageLoadingModule = ImageLoadingModule
        
        // When
        val imageLoader = imageLoadingModule.provideImageLoader(context)
        val diskCache = imageLoader.diskCache
        
        // Then
        assertNotNull("Disk cache should not be null", diskCache)
        assertEquals("Disk cache max size should be 100MB", 100 * 1024 * 1024L, diskCache?.maxSize ?: 0)
    }
}