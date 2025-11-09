package com.ytone.longcare.common.utils

import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.common.utils.DefaultMoshi
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class UriJsonAdapterTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = DefaultMoshi
    }

    @Test
    fun testUriSerialization() {
        // Given
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://test/uri"
        
        val adapter = moshi.adapter(Uri::class.java)
        
        // When
        val json = adapter.toJson(mockUri)
        
        // Then
        assertEquals("\"content://test/uri\"", json)
    }

    @Test
    fun testNullUriSerialization() {
        // Given
        val adapter = moshi.adapter(Uri::class.java)
        
        // When
        val json = adapter.toJson(null)
        
        // Then
        assertEquals("null", json)
    }

    @Test
    fun testImageTaskWithUriSerialization() {
        // Given
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://test/image"
        
        val imageTask = ImageTask(
            id = "test-id",
            originalUri = mockUri,
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.PROCESSING
        )
        
        val adapter = moshi.adapter(ImageTask::class.java)
        
        // When
        val json = adapter.toJson(imageTask)
        
        // Then
        assertTrue(json.contains("\"originalUri\":\"content://test/image\""))
        assertTrue(json.contains("\"id\":\"test-id\""))
    }
}