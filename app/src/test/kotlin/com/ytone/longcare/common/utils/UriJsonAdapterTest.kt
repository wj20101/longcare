package com.ytone.longcare.common.utils

import android.net.Uri
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UriJsonAdapterTest {

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `serializes Uri as its string value`() {
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns TEST_URI

        val json = DefaultMoshi.adapter(Uri::class.java).toJson(mockUri)

        assertEquals("\"$TEST_URI\"", json)
    }

    @Test
    fun `deserializes Uri string`() {
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>()
        every { Uri.parse(TEST_URI) } returns mockUri
        every { mockUri.toString() } returns TEST_URI

        val uri = DefaultMoshi.adapter(Uri::class.java).fromJson("\"$TEST_URI\"")

        assertNotNull(uri)
        assertEquals(TEST_URI, uri.toString())
    }

    @Test
    fun `serializes and deserializes null Uri`() {
        val adapter = DefaultMoshi.adapter(Uri::class.java)

        assertEquals("null", adapter.toJson(null))
        assertNull(adapter.fromJson("null"))
    }

    @Test
    fun `serializes ImageTask Uri strings`() {
        val imageTask = ImageTask(
            id = "test-id",
            originalUri = "content://original/123",
            taskType = ImageTaskType.BEFORE_CARE,
            resultUri = "content://result/456",
            status = ImageTaskStatus.PROCESSING,
        )

        val json = DefaultMoshi.adapter(ImageTask::class.java).toJson(imageTask)

        assertTrue(json.contains("\"id\":\"test-id\""))
        assertTrue(json.contains("\"originalUri\":\"content://original/123\""))
        assertTrue(json.contains("\"resultUri\":\"content://result/456\""))
        assertTrue(json.contains("\"status\":\"PROCESSING\""))
    }

    @Test
    fun `deserializes ImageTask Uri strings`() {
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

        val imageTask = DefaultMoshi.adapter(ImageTask::class.java).fromJson(json)

        assertNotNull(imageTask)
        assertEquals("test-id", imageTask?.id)
        assertEquals("content://original/123", imageTask?.originalUri)
        assertEquals("content://result/456", imageTask?.resultUri)
        assertEquals(ImageTaskType.BEFORE_CARE, imageTask?.taskType)
        assertEquals(ImageTaskStatus.PROCESSING, imageTask?.status)
    }

    private companion object {
        const val TEST_URI = "content://media/external/images/media/123"
    }
}
