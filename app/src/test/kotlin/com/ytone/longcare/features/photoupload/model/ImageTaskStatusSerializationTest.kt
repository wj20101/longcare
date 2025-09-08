package com.ytone.longcare.features.photoupload.model

import com.ytone.longcare.common.utils.DefaultMoshi
import org.junit.Test
import org.junit.Assert.*

/**
 * ImageTaskStatus 序列化测试
 * 验证枚举序列化和反序列化是否正常工作
 */
class ImageTaskStatusSerializationTest {

    @Test
    fun testImageTaskStatusSerialization() {
        val adapter = DefaultMoshi.adapter(ImageTaskStatus::class.java)
        
        // 测试 PROCESSING
        val processingJson = adapter.toJson(ImageTaskStatus.PROCESSING)
        assertEquals("\"PROCESSING\"", processingJson)
        
        // 测试 SUCCESS
        val successJson = adapter.toJson(ImageTaskStatus.SUCCESS)
        assertEquals("\"SUCCESS\"", successJson)
        
        // 测试 FAILED
        val failedJson = adapter.toJson(ImageTaskStatus.FAILED)
        assertEquals("\"FAILED\"", failedJson)
    }
    
    @Test
    fun testImageTaskStatusDeserialization() {
        val adapter = DefaultMoshi.adapter(ImageTaskStatus::class.java)
        
        // 测试反序列化 PROCESSING
        val processing = adapter.fromJson("\"PROCESSING\"")
        assertEquals(ImageTaskStatus.PROCESSING, processing)
        
        // 测试反序列化 SUCCESS
        val success = adapter.fromJson("\"SUCCESS\"")
        assertEquals(ImageTaskStatus.SUCCESS, success)
        
        // 测试反序列化 FAILED
        val failed = adapter.fromJson("\"FAILED\"")
        assertEquals(ImageTaskStatus.FAILED, failed)
    }
    
    @Test
    fun testImageTaskStatusInComplexStructure() {
        // 测试在复杂数据结构中的序列化（类似崩溃场景）
        // 直接测试包含ImageTaskStatus的Map结构
        val testData = mapOf(
            ImageTaskType.BEFORE_CARE to listOf(ImageTaskStatus.PROCESSING, ImageTaskStatus.SUCCESS),
            ImageTaskType.AFTER_CARE to listOf(ImageTaskStatus.SUCCESS, ImageTaskStatus.FAILED)
        )
        
        val mapType = com.squareup.moshi.Types.newParameterizedType(
            Map::class.java,
            ImageTaskType::class.java,
            com.squareup.moshi.Types.newParameterizedType(
                List::class.java,
                ImageTaskStatus::class.java
            )
        )
        val mapAdapter = DefaultMoshi.adapter<Map<ImageTaskType, List<ImageTaskStatus>>>(mapType)
        
        // 序列化
        val json = mapAdapter.toJson(testData)
        assertNotNull(json)
        assertTrue(json!!.contains("PROCESSING"))
        assertTrue(json.contains("SUCCESS"))
        assertTrue(json.contains("FAILED"))
        
        // 反序列化
        val deserializedData = mapAdapter.fromJson(json)
        assertNotNull(deserializedData)
        assertEquals(2, deserializedData!!.size)
        
        val beforeCareStatuses = deserializedData[ImageTaskType.BEFORE_CARE]
        assertNotNull(beforeCareStatuses)
        assertEquals(2, beforeCareStatuses!!.size)
        assertTrue(beforeCareStatuses.contains(ImageTaskStatus.PROCESSING))
        assertTrue(beforeCareStatuses.contains(ImageTaskStatus.SUCCESS))
        
        val afterCareStatuses = deserializedData[ImageTaskType.AFTER_CARE]
        assertNotNull(afterCareStatuses)
        assertEquals(2, afterCareStatuses!!.size)
        assertTrue(afterCareStatuses.contains(ImageTaskStatus.SUCCESS))
        assertTrue(afterCareStatuses.contains(ImageTaskStatus.FAILED))
    }
   @Test(expected = com.squareup.moshi.JsonDataException::class)
    fun testInvalidImageTaskStatusDeserialization() {
        // 测试无效的ImageTaskStatus值反序列化
        val adapter = DefaultMoshi.adapter(ImageTaskStatus::class.java)
        adapter.fromJson("\"INVALID_STATUS\"")
    }
}