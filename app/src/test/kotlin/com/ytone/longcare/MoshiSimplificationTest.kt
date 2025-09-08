package com.ytone.longcare

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test
import org.junit.Assert.*

/**
 * 测试Moshi简化配置方案
 */
class MoshiSimplificationTest {

    // 测试用的简化Moshi配置（只使用KotlinJsonAdapterFactory）
    private val simplifiedMoshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `test enum serialization with KotlinJsonAdapterFactory only`() {
        // 测试枚举序列化
        val testEnum = TestEnum.VALUE_ONE
        val adapter = simplifiedMoshi.adapter(TestEnum::class.java)
        
        val json = adapter.toJson(testEnum)
        assertNotNull(json)
        assertTrue(json.contains("VALUE_ONE"))
        
        val deserialized = adapter.fromJson(json)
        assertEquals(testEnum, deserialized)
    }

    @Test
    fun `test data class with JsonClass annotation`() {
        // 测试带@JsonClass注解的data class
        val testData = TestDataClass(
            id = "test123",
            name = "Test Name",
            type = TestEnum.VALUE_TWO
        )
        
        val adapter = simplifiedMoshi.adapter(TestDataClass::class.java)
        
        val json = adapter.toJson(testData)
        assertNotNull(json)
        assertTrue(json.contains("test123"))
        assertTrue(json.contains("Test Name"))
        assertTrue(json.contains("VALUE_TWO"))
        
        val deserialized = adapter.fromJson(json)
        assertEquals(testData, deserialized)
    }

    @Test
    fun `test data class without JsonClass annotation`() {
        // 测试不带@JsonClass注解的data class
        val testData = SimpleDataClass(
            value = "simple test",
            number = 42
        )
        
        val adapter = simplifiedMoshi.adapter(SimpleDataClass::class.java)
        
        val json = adapter.toJson(testData)
        assertNotNull(json)
        assertTrue(json.contains("simple test"))
        assertTrue(json.contains("42"))
        
        val deserialized = adapter.fromJson(json)
        assertEquals(testData, deserialized)
    }

    @Test
    fun `test complex nested structure`() {
        // 测试复杂嵌套结构
        val complexData = ComplexDataClass(
            simple = SimpleDataClass("nested", 100),
            testClass = TestDataClass("complex123", "Complex Name", TestEnum.VALUE_ONE),
            enumList = listOf(TestEnum.VALUE_ONE, TestEnum.VALUE_TWO),
            stringMap = mapOf("key1" to "value1", "key2" to "value2")
        )
        
        val adapter = simplifiedMoshi.adapter(ComplexDataClass::class.java)
        
        val json = adapter.toJson(complexData)
        assertNotNull(json)
        
        val deserialized = adapter.fromJson(json)
        assertEquals(complexData, deserialized)
    }
}

// 测试用的枚举
enum class TestEnum {
    VALUE_ONE,
    VALUE_TWO
}

// 带@JsonClass注解的测试data class
@JsonClass(generateAdapter = true)
data class TestDataClass(
    val id: String,
    val name: String,
    val type: TestEnum
)

// 不带@JsonClass注解的简单data class
data class SimpleDataClass(
    val value: String,
    val number: Int
)

// 复杂嵌套结构的data class
@JsonClass(generateAdapter = true)
data class ComplexDataClass(
    val simple: SimpleDataClass,
    val testClass: TestDataClass,
    val enumList: List<TestEnum>,
    val stringMap: Map<String, String>
)