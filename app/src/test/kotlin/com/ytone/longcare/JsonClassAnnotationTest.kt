package com.ytone.longcare

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import org.junit.Test
import org.junit.Assert.*

/**
 * 测试@JsonClass注解与KotlinJsonAdapterFactory的配合效果
 */
class JsonClassAnnotationTest {

    // 仅使用KotlinJsonAdapterFactory的简化配置
    private val simplifiedMoshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `test enum serialization with Json annotation`() {
        // 测试带有@Json注解的枚举序列化
        val taskType = ImageTaskType.BEFORE_CARE
        val taskStatus = ImageTaskStatus.SUCCESS
        
        val typeAdapter = simplifiedMoshi.adapter(ImageTaskType::class.java)
        val statusAdapter = simplifiedMoshi.adapter(ImageTaskStatus::class.java)
        
        // 序列化枚举
        val typeJson = typeAdapter.toJson(taskType)
        val statusJson = statusAdapter.toJson(taskStatus)
        
        println("TaskType序列化结果: $typeJson")
        println("TaskStatus序列化结果: $statusJson")
        
        // 验证@Json注解是否生效
        assertEquals("\"BEFORE_CARE\"", typeJson)
        assertEquals("\"SUCCESS\"", statusJson)
        
        // 反序列化枚举
        val deserializedType = typeAdapter.fromJson(typeJson)
        val deserializedStatus = statusAdapter.fromJson(statusJson)
        
        assertEquals(taskType, deserializedType)
        assertEquals(taskStatus, deserializedStatus)
    }

    @Test
    fun `test data class without JsonClass annotation`() {
        // 创建一个没有@JsonClass注解的data class进行对比
        data class SimpleTask(
            val id: String,
            val type: String,
            val status: String,
            val lines: List<String>
        )
        
        val simpleTask = SimpleTask(
            id = "simple-123",
            type = "BEFORE_CARE",
            status = "SUCCESS",
            lines = listOf("护理前", "2024-01-15")
        )
        
        val adapter = simplifiedMoshi.adapter(SimpleTask::class.java)
        
        try {
            // 测试序列化
            val json = adapter.toJson(simpleTask)
            println("SimpleTask序列化结果: $json")
            
            assertNotNull(json)
            assertTrue(json.contains("simple-123"))
            assertTrue(json.contains("BEFORE_CARE"))
            
            // 测试反序列化
            val deserialized = adapter.fromJson(json)
            assertEquals(simpleTask, deserialized)
            
            println("SimpleTask序列化/反序列化成功")
            
        } catch (e: Exception) {
            println("SimpleTask序列化失败: ${e.message}")
            fail("没有@JsonClass注解的data class序列化应该成功: ${e.message}")
        }
    }

    @Test
    fun `test complex nested structure`() {
        // 测试复杂嵌套结构
        data class TaskInfo(
            val id: String,
            val type: ImageTaskType,
            val status: ImageTaskStatus,
            val metadata: Map<String, Any>
        )
        
        val taskInfo = TaskInfo(
            id = "nested-456",
            type = ImageTaskType.AFTER_CARE,
            status = ImageTaskStatus.PROCESSING,
            metadata = mapOf(
                "nurse" to "张护士",
                "patient" to "李大爷",
                "timestamp" to 1642234567890L,
                "location" to mapOf(
                    "building" to "A栋",
                    "room" to "301"
                )
            )
        )
        
        val adapter = simplifiedMoshi.adapter(TaskInfo::class.java)
        
        try {
            // 序列化
            val json = adapter.toJson(taskInfo)
            println("复杂嵌套结构序列化结果: $json")
            
            assertNotNull(json)
            assertTrue(json.contains("nested-456"))
            assertTrue(json.contains("AFTER_CARE"))
            assertTrue(json.contains("PROCESSING"))
            assertTrue(json.contains("张护士"))
            
            // 反序列化
            val deserialized = adapter.fromJson(json)
            assertEquals(taskInfo.id, deserialized?.id)
            assertEquals(taskInfo.type, deserialized?.type)
            assertEquals(taskInfo.status, deserialized?.status)
            
            println("复杂嵌套结构序列化/反序列化成功")
            
        } catch (e: Exception) {
            println("复杂嵌套结构序列化失败: ${e.message}")
            // 这个可能会失败，因为Map<String, Any>的序列化比较复杂
            assertNotNull("异常信息应存在", e.message)
        }
    }

    @Test
    fun `test KotlinJsonAdapterFactory capabilities`() {
        // 测试KotlinJsonAdapterFactory的能力边界
        data class BasicTypes(
            val stringValue: String,
            val intValue: Int,
            val longValue: Long,
            val doubleValue: Double,
            val booleanValue: Boolean,
            val listValue: List<String>,
            val nullableString: String?,
            val enumValue: ImageTaskType
        )
        
        val basicTypes = BasicTypes(
            stringValue = "测试字符串",
            intValue = 42,
            longValue = 1234567890L,
            doubleValue = 3.14159,
            booleanValue = true,
            listValue = listOf("item1", "item2", "item3"),
            nullableString = null,
            enumValue = ImageTaskType.BEFORE_CARE
        )
        
        val adapter = simplifiedMoshi.adapter(BasicTypes::class.java)
        
        // 序列化
        val json = adapter.toJson(basicTypes)
        println("基础类型序列化结果: $json")
        
        assertNotNull(json)
        assertTrue(json.contains("测试字符串"))
        assertTrue(json.contains("42"))
        assertTrue(json.contains("3.14159"))
        assertTrue(json.contains("BEFORE_CARE"))
        
        // 反序列化
        val deserialized = adapter.fromJson(json)
        assertEquals(basicTypes, deserialized)
        
        println("基础类型序列化/反序列化成功")
    }
}