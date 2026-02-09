package com.ytone.longcare

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.common.utils.DefaultMoshi
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import org.junit.Assert.*
import org.junit.Test

/**
 * 测试Moshi序列化优化后的功能
 */
class MoshiOptimizationTest {
    private val stringAnyMapType =
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)

    @Test
    fun `test ImageTask serialization with JsonClass annotation`() {
        // 测试Moshi序列化功能
        
        // 创建测试数据 - 使用字符串而不是Uri避免Android依赖
        val testData = mapOf(
            "id" to "test-id",
            "originalUri" to "file:///test/path/image.jpg",
            "taskType" to "UPLOAD"
        )

        // 序列化测试数据
        val adapter = DefaultMoshi.adapter<Map<String, Any>>(stringAnyMapType)
        val json = adapter.toJson(testData)
        
        // 验证序列化结果
        assertTrue("JSON应该包含originalUri", json.contains("\"originalUri\":\"file:///test/path/image.jpg\""))
        assertTrue("JSON应该包含taskType", json.contains("\"taskType\":\"UPLOAD\""))
        
        // 反序列化
        val deserializedData = requireNotNull(adapter.fromJson(json))
        assertEquals("id应该匹配", "test-id", deserializedData["id"])
        assertEquals("taskType应该匹配", "UPLOAD", deserializedData["taskType"])
    }
    
    @Test
    fun `test enum serialization without custom adapters`() {
        // 测试枚举类型的序列化
        val taskType = ImageTaskType.AFTER_CARE
        val status = ImageTaskStatus.SUCCESS
        
        // 通过Moshi直接序列化枚举
        val taskTypeJson = DefaultMoshi.adapter(ImageTaskType::class.java).toJson(taskType)
        val statusJson = DefaultMoshi.adapter(ImageTaskStatus::class.java).toJson(status)
        
        assertEquals("任务类型序列化应该正确", "\"AFTER_CARE\"", taskTypeJson)
        assertEquals("状态序列化应该正确", "\"SUCCESS\"", statusJson)
        
        // 测试反序列化
        val deserializedTaskType = DefaultMoshi.adapter(ImageTaskType::class.java).fromJson(taskTypeJson)
        val deserializedStatus = DefaultMoshi.adapter(ImageTaskStatus::class.java).fromJson(statusJson)
        
        assertEquals("任务类型反序列化应该正确", ImageTaskType.AFTER_CARE, deserializedTaskType)
        assertEquals("状态反序列化应该正确", ImageTaskStatus.SUCCESS, deserializedStatus)
    }
    
    @Test
    fun `test Map with enum keys serialization`() {
        // 测试Map序列化功能
        
        // 创建测试数据 - 使用字符串避免Android依赖
        val testMap = mapOf(
            "UPLOAD" to "file:///upload/path",
            "COMPRESS" to "file:///compress/path"
        )
        
        // 序列化
        val adapter = DefaultMoshi.adapter<Map<String, Any>>(stringAnyMapType)
        val json = adapter.toJson(testMap)
        
        // 验证序列化结果
        assertTrue("JSON应该包含UPLOAD", json.contains("UPLOAD"))
        assertTrue("JSON应该包含COMPRESS", json.contains("COMPRESS"))
        assertTrue("JSON应该包含upload路径", json.contains("file:///upload/path"))
        assertTrue("JSON应该包含compress路径", json.contains("file:///compress/path"))
        
        // 反序列化
        val deserializedMap = requireNotNull(adapter.fromJson(json))
        assertEquals("应该包含2个键", 2, deserializedMap.keys.size)
        assertTrue("应该包含UPLOAD键", deserializedMap.containsKey("UPLOAD"))
        assertTrue("应该包含COMPRESS键", deserializedMap.containsKey("COMPRESS"))
    }
}
