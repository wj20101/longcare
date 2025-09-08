package com.ytone.longcare

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import org.junit.Test
import org.junit.Assert.*

/**
 * 测试ImageTask在简化Moshi配置下的序列化表现
 */
class ImageTaskSimplificationTest {

    // 简化的Moshi配置（不包含自定义Uri适配器）
    private val simplifiedMoshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `test ImageTask serialization without Uri`() {
        // 创建一个不包含Uri的ImageTask测试数据
        val testData = mapOf(
            "id" to "test123",
            "originalUri" to "file:///test/path/image.jpg",
            "taskType" to "BEFORE_CARE",
            "watermarkLines" to listOf("Line 1", "Line 2"),
            "resultUri" to "file:///test/path/result.jpg",
            "status" to "SUCCESS",
            "errorMessage" to null,
            "isUploaded" to true,
            "key" to "test-key",
            "cloudUrl" to "https://example.com/image.jpg"
        )
        
        val adapter = simplifiedMoshi.adapter(Map::class.java)
        
        // 测试序列化
        val json = adapter.toJson(testData)
        assertNotNull(json)
        assertTrue(json.contains("test123"))
        assertTrue(json.contains("BEFORE_CARE"))
        assertTrue(json.contains("SUCCESS"))
        
        // 测试反序列化
        val deserialized = adapter.fromJson(json) as Map<String, Any?>
        assertEquals("test123", deserialized["id"])
        assertEquals("BEFORE_CARE", deserialized["taskType"])
        assertEquals("SUCCESS", deserialized["status"])
    }

    @Test
    fun `test enum serialization with KotlinJsonAdapterFactory`() {
        // 测试枚举类型的序列化
        val taskType = ImageTaskType.BEFORE_CARE
        val taskStatus = ImageTaskStatus.SUCCESS
        
        val typeAdapter = simplifiedMoshi.adapter(ImageTaskType::class.java)
        val statusAdapter = simplifiedMoshi.adapter(ImageTaskStatus::class.java)
        
        // 序列化枚举
        val typeJson = typeAdapter.toJson(taskType)
        val statusJson = statusAdapter.toJson(taskStatus)
        
        assertNotNull(typeJson)
        assertNotNull(statusJson)
        assertTrue(typeJson.contains("BEFORE_CARE"))
        assertTrue(statusJson.contains("SUCCESS"))
        
        // 反序列化枚举
        val deserializedType = typeAdapter.fromJson(typeJson)
        val deserializedStatus = statusAdapter.fromJson(statusJson)
        
        assertEquals(taskType, deserializedType)
        assertEquals(taskStatus, deserializedStatus)
    }

    @Test
    fun `test List and Map serialization`() {
        // 测试List和Map的序列化
        val watermarkLines = listOf("护理前", "2024-01-15 10:30")
        val metadata = mapOf(
            "location" to "北京市朝阳区",
            "device" to "Android",
            "version" to "1.0.0"
        )
        
        val listAdapter = simplifiedMoshi.adapter(List::class.java)
        val mapAdapter = simplifiedMoshi.adapter(Map::class.java)
        
        // 序列化
        val listJson = listAdapter.toJson(watermarkLines)
        val mapJson = mapAdapter.toJson(metadata)
        
        assertNotNull(listJson)
        assertNotNull(mapJson)
        assertTrue(listJson.contains("护理前"))
        assertTrue(mapJson.contains("北京市朝阳区"))
        
        // 反序列化
        val deserializedList = listAdapter.fromJson(listJson) as List<String>
        val deserializedMap = mapAdapter.fromJson(mapJson) as Map<String, String>
        
        assertEquals(2, deserializedList.size)
        assertEquals("护理前", deserializedList[0])
        assertEquals("北京市朝阳区", deserializedMap["location"])
    }

    @Test
    fun `test complex nested structure without custom adapters`() {
        // 测试复杂嵌套结构（不使用自定义适配器）
        val complexData = mapOf(
            "task" to mapOf(
                "id" to "complex123",
                "type" to "AFTER_CARE",
                "status" to "PROCESSING",
                "watermarks" to listOf("护理后", "2024-01-15 15:30"),
                "metadata" to mapOf(
                    "nurse" to "张护士",
                    "patient" to "李大爷"
                )
            ),
            "upload" to mapOf(
                "isUploaded" to false,
                "progress" to 0.75,
                "retryCount" to 2
            )
        )
        
        val adapter = simplifiedMoshi.adapter(Map::class.java)
        
        // 序列化
        val json = adapter.toJson(complexData)
        assertNotNull(json)
        assertTrue(json.contains("complex123"))
        assertTrue(json.contains("AFTER_CARE"))
        assertTrue(json.contains("张护士"))
        
        // 反序列化
        val deserialized = adapter.fromJson(json) as Map<String, Any?>
        val task = deserialized["task"] as Map<String, Any?>
        val upload = deserialized["upload"] as Map<String, Any?>
        
        assertEquals("complex123", task["id"])
        assertEquals("AFTER_CARE", task["type"])
        assertEquals(false, upload["isUploaded"])
    }
}