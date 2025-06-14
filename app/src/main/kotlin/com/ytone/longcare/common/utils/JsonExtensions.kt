package com.ytone.longcare.common.utils

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/**
 * Default Json instance for convenience.
 */
val DefaultJson: Json = Json {
    isLenient = true             // 对宽松的JSON格式更友好
    ignoreUnknownKeys = true     // 忽略JSON中存在但DTO中不存在的字段
    coerceInputValues = true     // 如果JSON字段为null，但DTO中对应字段不可空且有默认值，会尝试使用默认值
    encodeDefaults = false       // 序列化时是否包含具有默认值的属性 (false更常见，节省空间)
}

// --- 标准序列化函数 (无需修改) ---
/**
 * Converts a serializable object of type [T] to its JSON string representation.
 */
inline fun <reified T> T?.toJsonString(json: Json = DefaultJson): String? {
    if (this == null) return null
    return try {
        json.encodeToString(this)
    } catch (e: SerializationException) {
        logE(message = "Json Fail", throwable = e)
        null
    }
}

/**
 * Converts a JSON string to an object of type [T].
 */
inline fun <reified T> String?.toObject(json: Json = DefaultJson): T? {
    if (this.isNullOrBlank()) return null
    return try {
        json.decodeFromString<T>(this)
    } catch (e: SerializationException) {
        logE(message = "Json Fail", throwable = e)
        null
    }
}

// ... 其他标准函数 (fromJsonToList 等) ...
inline fun <reified T> String?.fromJsonToList(json: Json = DefaultJson): List<T>? {
    if (this.isNullOrBlank()) return null
    return try {
        json.decodeFromString<List<T>>(this)
    } catch (e: SerializationException) {
        logE(message = "Json Fail", throwable = e)
        null
    }
}

inline fun <reified K, reified V> String?.fromJsonToMap(json: Json = DefaultJson): Map<K, V>? {
    if (this.isNullOrBlank()) return null
    return try {
        json.decodeFromString<Map<K, V>>(this)
    } catch (e: SerializationException) {
        logE(message = "Json Fail", throwable = e)
        null
    }
}

// --- 智能的 Map 序列化函数 ---

/**
 * [智能] 将任意 Map 转换为 JSON 字符串。
 *
 * 此函数会自动检测键 (K) 或值 (V) 的类型。
 * - 如果 K 或 V 是 Any 类型，它会使用动态、逐个转换的方式来生成 JSON。
 * - 如果 K 和 V 都是具体的、可序列化的类型，它会使用高效的、编译时生成的标准序列化器。
 *
 * @param json 用于序列化的 Json 实例。
 * @return JSON 字符串，如果序列化失败则返回 null。
 */
inline fun <reified K, reified V> Map<K, V>?.toJsonStringMap(json: Json = DefaultJson): String? {
    if (this == null) return null

    // 检查 K 或 V 是否为不明确的 Any 类型
    return if (K::class == Any::class || V::class == Any::class) {
        // 如果是，则使用支持任意键值的动态序列化方法
        this.toDynamicJsonString(json)
    } else {
        // 否则，使用标准的高效序列化方法
        try {
            json.encodeToString(this)
        } catch (e: SerializationException) {
            logE(message = "Json Fail", throwable = e)
            // 如果标准方法失败（例如，类型没有@Serializable注解），尝试降级到动态方法
            this.toDynamicJsonString(json)
        }
    }
}

// --- 用于动态处理的内部辅助函数 ---

/**
 * 这是动态转换的入口点。
 */
fun Map<*, *>?.toDynamicJsonString(json: Json = DefaultJson): String? {
    if (this == null) return null
    return json.encodeToString(this.toDynamicJsonElement())
}

/**
 * 定义为 private 以作为内部实现。
 */
private fun Map<*, *>.toDynamicJsonElement(): JsonElement {
    val map = mutableMapOf<String, JsonElement>()
    this.forEach { (key, value) ->
        // 将任意类型的 key 通过 toString() 转换为 JSON 对象的字符串 key
        map[key.toString()] = value.toDynamicJsonElement()
    }
    return JsonObject(map)
}

/**
 * 将任意类型 (`Any?`) 递归转换为一个 JsonElement。
 * 这是手动处理的核心，并采用了“混合模式”。
 */
private fun Any?.toDynamicJsonElement(): JsonElement {
    return when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> this.toDynamicJsonElement() // 递归处理 Map
        is Iterable<*> -> JsonArray(this.map { it.toDynamicJsonElement() }) // 递归处理 List 等 Iterable
        is Array<*> -> JsonArray(this.map { it.toDynamicJsonElement() }) // 递归处理 Array
        else -> {
            // 混合模式：优先尝试标准序列化，失败则降级
            try {
                val serializer = DefaultJson.serializersModule.serializer(this::class.java)
                DefaultJson.encodeToJsonElement(serializer, this)
            } catch (e: Exception) {
                logE(message = "Json Fail", throwable = e)
                JsonPrimitive(this.toString())
            }
        }
    }
}