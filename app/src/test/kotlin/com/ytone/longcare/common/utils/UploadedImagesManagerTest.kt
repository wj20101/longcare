package com.ytone.longcare.common.utils

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import android.net.Uri
import io.mockk.mockkStatic
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * UploadedImagesManager 单元测试
 */
class UploadedImagesManagerTest {

    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var moshi: Moshi
    private lateinit var uploadedImagesManager: UploadedImagesManager

    @Before
    fun setup() {
        mockSharedPreferences = mockk()
        mockEditor = mockk(relaxed = true)
        moshi = Moshi.Builder().build()
        uploadedImagesManager = UploadedImagesManager(mockSharedPreferences, moshi)

        every { mockSharedPreferences.edit() } returns mockEditor
    }

    @Test
    fun `saveUploadedImages should save images to SharedPreferences`() {
        // Given
        mockkStatic(Uri::class)
        val mockUri1 = mockk<Uri>()
        val mockUri2 = mockk<Uri>()
        every { Uri.parse(any()) } returns mockUri1
        every { mockUri1.toString() } returns "content://test1"
        every { mockUri2.toString() } returns "content://test2"
        
        val orderRequest = OrderInfoRequestModel(orderId = 12345L, planId = 1)
        val imageTask1 = ImageTask(
            id = "1",
            originalUri = mockUri1,
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.SUCCESS
        )
        val imageTask2 = ImageTask(
            id = "2",
            originalUri = mockUri2,
            taskType = ImageTaskType.AFTER_CARE,
            status = ImageTaskStatus.SUCCESS
        )
        val uploadedImages = mapOf(
            ImageTaskType.BEFORE_CARE to listOf(imageTask1),
            ImageTaskType.AFTER_CARE to listOf(imageTask2)
        )
        val type = Types.newParameterizedType(Map::class.java, ImageTaskType::class.java, 
            Types.newParameterizedType(List::class.java, ImageTask::class.java))
        val adapter = moshi.adapter<Map<ImageTaskType, List<ImageTask>>>(type)
        val expectedJson = adapter.toJson(uploadedImages)
        
        // When
        uploadedImagesManager.saveUploadedImages(orderRequest, uploadedImages)
        
        // Then
        verify { mockEditor.putString("uploaded_images_12345", expectedJson) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `getUploadedImages should return saved images`() {
        // Given
        val orderRequest = OrderInfoRequestModel(orderId = 12345L, planId = 1)
        val json = """
            {
                "BEFORE_CARE": [
                    {
                        "id": "1",
                        "originalUri": "content://test1",
                        "taskType": "BEFORE_CARE",
                        "resultUri": null,
                        "status": "SUCCESS",
                        "errorMessage": null,
                        "isUploaded": false,
                        "key": null,
                        "cloudUrl": null
                    }
                ]
            }
        """.trimIndent()
        every { mockSharedPreferences.getString("uploaded_images_12345", null) } returns json

        // When
        val result = uploadedImagesManager.getUploadedImages(orderRequest)

        // Then
        assertEquals(1, result.size)
        assertTrue(result.containsKey(ImageTaskType.BEFORE_CARE))
        assertEquals(1, result[ImageTaskType.BEFORE_CARE]?.size)
    }

    @Test
    fun `getUploadedImages should return empty map when no data exists`() {
        // Given
        val orderRequest = OrderInfoRequestModel(orderId = 12345L, planId = 1)
        every { mockSharedPreferences.getString("uploaded_images_12345", null) } returns null

        // When
        val result = uploadedImagesManager.getUploadedImages(orderRequest)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `hasUploadedImages should return true when images exist`() {
        // Given
        val orderRequest = OrderInfoRequestModel(orderId = 12345L, planId = 1)
        val json = """
            {
                "BEFORE_CARE": [
                    {
                        "id": "1",
                        "originalUri": "content://test1",
                        "taskType": "BEFORE_CARE",
                        "resultUri": null,
                        "status": "SUCCESS",
                        "errorMessage": null,
                        "isUploaded": false,
                        "key": null,
                        "cloudUrl": null
                    }
                ]
            }
        """.trimIndent()
        every { mockSharedPreferences.getString("uploaded_images_12345", null) } returns json

        // When
        val result = uploadedImagesManager.hasUploadedImages(orderRequest)

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasUploadedImages should return false when no images exist`() {
        // Given
        val orderRequest = OrderInfoRequestModel(orderId = 12345L, planId = 1)
        every { mockSharedPreferences.getString("uploaded_images_12345", null) } returns null

        // When
        val result = uploadedImagesManager.hasUploadedImages(orderRequest)

        // Then
        assertFalse(result)
    }

    @Test
    fun `deleteUploadedImages should remove images from SharedPreferences`() {
        // Given
        val orderRequest = OrderInfoRequestModel(orderId = 12345L, planId = 1)

        // When
        uploadedImagesManager.deleteUploadedImages(orderRequest)

        // Then
        verify { mockEditor.remove("uploaded_images_12345") }
        verify { mockEditor.apply() }
    }
}