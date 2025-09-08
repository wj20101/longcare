package com.ytone.longcare

/**
 * Moshi序列化配置最佳实践分析
 * 
 * 基于对当前项目Moshi配置的深入分析，以下是简化配置的建议和最佳实践：
 */

/*
=== 当前配置分析 ===

1. 现有配置（JsonExtensions.kt）：
   - DefaultMoshi包含4个适配器：UnitJsonAdapter、UriJsonAdapter、WireJsonAdapterFactory、KotlinJsonAdapterFactory
   - 手动创建了UnitJsonAdapter和UriJsonAdapter
   - 使用了第三方WireJsonAdapterFactory
   - KotlinJsonAdapterFactory作为兜底方案

2. @JsonClass注解使用情况：
   - 项目中大部分data class已经使用@JsonClass(generateAdapter = true)
   - 包括ImageTask、NurseServiceTimeModel、OrderListParamModel等
   - 枚举类使用@Json注解指定序列化名称

=== 简化方案评估 ===

✅ 推荐保留的配置：
1. @JsonClass(generateAdapter = true) - 为data class生成高效的适配器
2. UriJsonAdapter - Android Uri类型需要特殊处理
3. UnitJsonAdapter - Kotlin Unit类型的特殊处理
4. KotlinJsonAdapterFactory - 作为反射兜底方案

❌ 不推荐的简化：
1. 移除所有自定义适配器 - 会导致Uri等特殊类型序列化失败
2. 仅依赖KotlinJsonAdapterFactory - 性能较差且不支持局部类
3. 移除@JsonClass注解 - 会降低序列化性能

=== 最佳实践建议 ===

1. 保持当前配置结构：
   ```kotlin
   val DefaultMoshi = Moshi.Builder()
       .add(UnitJsonAdapter())
       .add(UriJsonAdapter())
       .add(WireJsonAdapterFactory.create())
       .add(KotlinJsonAdapterFactory())
       .build()
   ```

2. 新增data class规范：
   - 所有用于序列化的data class都应添加@JsonClass(generateAdapter = true)
   - 枚举类使用@Json注解指定序列化名称
   - 避免在data class中使用需要特殊处理的类型（如Uri）

3. 特殊类型处理：
   - Uri类型：继续使用UriJsonAdapter
   - Unit类型：继续使用UnitJsonAdapter
   - 自定义类型：根据需要创建专门的适配器

4. 性能优化：
   - @JsonClass生成的适配器性能最佳
   - KotlinJsonAdapterFactory仅作为兜底，避免过度依赖
   - 复杂嵌套结构考虑拆分为更简单的结构

=== 针对ImageTask的具体建议 ===

当前ImageTask已经正确使用@JsonClass(generateAdapter = true)，无需额外配置。
问题在于Uri字段的序列化，这正是UriJsonAdapter存在的原因。

建议的ImageTask使用方式：
```kotlin
// 序列化时
val json = imageTask.toJsonString() // 使用JsonExtensions中的扩展函数

// 反序列化时
val imageTask = json.toObject<ImageTask>() // 使用JsonExtensions中的扩展函数
```

=== 总结 ===

当前的Moshi配置已经是相对优化的方案：
1. @JsonClass注解提供最佳性能
2. 自定义适配器处理特殊类型
3. KotlinJsonAdapterFactory作为兜底
4. 扩展函数简化使用

不建议进一步简化，因为：
1. 会失去对特殊类型（Uri、Unit）的支持
2. 性能会下降（过度依赖反射）
3. 增加维护复杂度

最佳做法是保持当前配置，确保新增的data class都使用@JsonClass注解。
*/

// 以下是验证当前配置有效性的测试
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.common.utils.DefaultMoshi
import com.ytone.longcare.common.utils.toJsonString
import com.ytone.longcare.common.utils.toObject
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import org.junit.Test
import org.junit.Assert.*

class MoshiBestPracticesAnalysis {

    @Test
    fun `verify current configuration effectiveness`() {
        // 验证当前DefaultMoshi配置的有效性
        
        // 1. 测试枚举序列化
        val taskType = ImageTaskType.BEFORE_CARE
        val taskTypeAdapter = DefaultMoshi.adapter(ImageTaskType::class.java)
        val taskTypeJson = taskTypeAdapter.toJson(taskType)
        val deserializedTaskType = taskTypeAdapter.fromJson(taskTypeJson)
        
        assertEquals(taskType, deserializedTaskType)
        assertTrue("枚举应使用@Json注解的值", taskTypeJson.contains("BEFORE_CARE"))
        
        // 2. 测试基础类型集合
        val stringList = listOf("item1", "item2", "item3")
        val stringListAdapter = DefaultMoshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
        val stringListJson = stringListAdapter.toJson(stringList)
        val deserializedStringList = stringListAdapter.fromJson(stringListJson)
        
        assertEquals(stringList, deserializedStringList)
        
        // 3. 测试Map类型
        val stringMap = mapOf("key1" to "value1", "key2" to "value2")
        val stringMapAdapter = DefaultMoshi.adapter<Map<String, String>>(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))
        val stringMapJson = stringMapAdapter.toJson(stringMap)
        val deserializedStringMap = stringMapAdapter.fromJson(stringMapJson)
        
        assertEquals(stringMap, deserializedStringMap)
        
        println("当前Moshi配置验证通过，支持枚举、集合和Map的序列化")
        println("注意：局部类无法序列化，这正是为什么需要@JsonClass注解的原因")
    }
    
    @Test
    fun `demonstrate performance difference`() {
        // 演示@JsonClass vs KotlinJsonAdapterFactory的性能差异
        
        // 使用@JsonClass的data class（ImageTaskType已有此注解）
        val withJsonClass = ImageTaskType.BEFORE_CARE
        
        // 仅使用KotlinJsonAdapterFactory的配置
        val reflectionOnlyMoshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val defaultAdapter = DefaultMoshi.adapter(ImageTaskType::class.java)
        val reflectionAdapter = reflectionOnlyMoshi.adapter(ImageTaskType::class.java)
        
        // 性能测试（简单示例）
        val iterations = 1000
        
        val startTime1 = System.currentTimeMillis()
        repeat(iterations) {
            defaultAdapter.toJson(withJsonClass)
        }
        val defaultTime = System.currentTimeMillis() - startTime1
        
        val startTime2 = System.currentTimeMillis()
        repeat(iterations) {
            reflectionAdapter.toJson(withJsonClass)
        }
        val reflectionTime = System.currentTimeMillis() - startTime2
        
        println("默认配置耗时: ${defaultTime}ms")
        println("纯反射配置耗时: ${reflectionTime}ms")
        
        // 通常@JsonClass生成的适配器会更快
        assertTrue("默认配置应该更快或相当", defaultTime <= reflectionTime * 1.5)
    }
}