package com.ytone.longcare.di

import android.net.Uri
import com.squareup.moshi.Moshi
import com.ytone.longcare.common.utils.DefaultMoshi
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Test
import org.junit.Assert.*

/**
 * UriJsonAdapter 的单元测试
 */
class UriJsonAdapterTest {

    @Test
    fun testUriSerialization() {
        // 模拟 Uri
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://media/external/images/media/123"
        
        // 序列化
        val adapter = DefaultMoshi.adapter(Uri::class.java)
        val json = adapter.toJson(mockUri)
        
        assertEquals("\"content://media/external/images/media/123\"", json)
    }
    
    @Test
    fun testUriDeserialization() {
        val json = "\"content://media/external/images/media/123\""
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://media/external/images/media/123"
        mockkStatic(Uri::class)
        every { Uri.parse("content://media/external/images/media/123") } returns mockUri
        
        // 反序列化
        val adapter = DefaultMoshi.adapter(Uri::class.java)
        val uri = adapter.fromJson(json)
        
        assertNotNull(uri)
        assertEquals("content://media/external/images/media/123", uri.toString())
    }
    
    @Test
    fun testNullUriSerialization() {
        val adapter = DefaultMoshi.adapter(Uri::class.java)
        val json = adapter.toJson(null)
        
        assertEquals("null", json)
    }
    
    @Test
    fun testNullUriDeserialization() {
        val json = "null"
        
        val adapter = DefaultMoshi.adapter(Uri::class.java)
        val uri = adapter.fromJson(json)
        
        assertNull(uri)
    }
    
    @Test
    fun testImageTaskWithUriSerialization() {
        // 模拟 Uri
        val mockOriginalUri = mockk<Uri>()
        val mockResultUri = mockk<Uri>()
        every { mockOriginalUri.toString() } returns "content://original/123"
        every { mockResultUri.toString() } returns "content://result/456"
        
        val imageTask = ImageTask(
            id = "test-id",
            originalUri = mockOriginalUri,
            taskType = ImageTaskType.BEFORE_CARE,
            resultUri = mockResultUri
        )
        
        // 序列化 ImageTask
        val adapter = DefaultMoshi.adapter(ImageTask::class.java)
        val json = adapter.toJson(imageTask)
        
        assertNotNull(json)
        assertTrue(json!!.contains("content://original/123"))
        assertTrue(json.contains("content://result/456"))
    }
    
    @Test
    fun testImageTaskWithUriDeserialization() {
        val mockOriginalUri = mockk<Uri>()
        val mockResultUri = mockk<Uri>()
        every { mockOriginalUri.toString() } returns "content://original/123"
        every { mockResultUri.toString() } returns "content://result/456"
        mockkStatic(Uri::class)
        every { Uri.parse("content://original/123") } returns mockOriginalUri
        every { Uri.parse("content://result/456") } returns mockResultUri

        val json = """
            {
                "id": "test-id",
                "originalUri": "content://original/123",
                "taskType": "BEFORE_CARE",
                "resultUri": "content://result/456",
                "status": "PROCESSING",
                "errorMessage": null,
                "isUploaded": false,
                "key": null,
                "cloudUrl": null
            }
        """.trimIndent()
        
        // 反序列化 ImageTask
        val adapter = DefaultMoshi.adapter(ImageTask::class.java)
        val imageTask = adapter.fromJson(json)
        
        assertNotNull(imageTask)
        assertEquals("test-id", imageTask!!.id)
        assertEquals("content://original/123", imageTask.originalUri.toString())
        assertEquals("content://result/456", imageTask.resultUri.toString())
        assertEquals(ImageTaskType.BEFORE_CARE, imageTask.taskType)
    }
}
