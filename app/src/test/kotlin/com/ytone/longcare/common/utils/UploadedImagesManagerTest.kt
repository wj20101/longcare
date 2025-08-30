package com.ytone.longcare.common.utils

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.features.photoupload.model.ImageTaskType
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
        val orderRequest = OrderInfoRequestModel(orderId = 12345L, planId = 1)
        val uploadedImages = mapOf(
            ImageTaskType.BEFORE_CARE to listOf("url1", "url2"),
            ImageTaskType.AFTER_CARE to listOf("url3", "url4")
        )
        val type = Types.newParameterizedType(Map::class.java, ImageTaskType::class.java, 
            Types.newParameterizedType(List::class.java, String::class.java))
        val adapter = moshi.adapter<Map<ImageTaskType, List<String>>>(type)
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
        val uploadedImages = mapOf(
            ImageTaskType.BEFORE_CARE to listOf("url1", "url2"),
            ImageTaskType.AFTER_CARE to listOf("url3", "url4")
        )
        val type = Types.newParameterizedType(Map::class.java, ImageTaskType::class.java, 
            Types.newParameterizedType(List::class.java, String::class.java))
        val adapter = moshi.adapter<Map<ImageTaskType, List<String>>>(type)
        val json = adapter.toJson(uploadedImages)
        every { mockSharedPreferences.getString("uploaded_images_12345", null) } returns json

        // When
        val result = uploadedImagesManager.getUploadedImages(orderRequest)

        // Then
        assertEquals(uploadedImages, result)
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
        val uploadedImages = mapOf(
            ImageTaskType.BEFORE_CARE to listOf("url1", "url2")
        )
        val type = Types.newParameterizedType(Map::class.java, ImageTaskType::class.java, 
            Types.newParameterizedType(List::class.java, String::class.java))
        val adapter = moshi.adapter<Map<ImageTaskType, List<String>>>(type)
        val json = adapter.toJson(uploadedImages)
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