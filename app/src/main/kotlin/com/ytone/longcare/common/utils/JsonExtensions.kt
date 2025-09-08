package com.ytone.longcare.common.utils

import android.net.Uri
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.wire.WireJsonAdapterFactory
import com.ytone.longcare.di.UnitJsonAdapter
import com.ytone.longcare.di.UriJsonAdapter
import java.lang.reflect.Type

/**
 * 提供一个默认的 Moshi 实例，已配置 Kotlin 支持。
 * 在实际项目中，通过 Hilt 注入 Moshi 实例是更好的做法。
 */
val DefaultMoshi: Moshi = Moshi.Builder()
    .add(Unit::class.java, UnitJsonAdapter)
    .add(Uri::class.java, UriJsonAdapter()) // 添加 Uri 的自定义适配器
    .add(WireJsonAdapterFactory()) // 添加 WireJsonAdapterFactory 来处理所有 Wire 生成的类型
    .add(KotlinJsonAdapterFactory()) // 添加 KotlinJsonAdapterFactory 来处理常规的 Kotlin 类
    .build()

/**
 * 将任意对象（类型 T）转换为 JSON 字符串。
 * @param T 对象的类型。
 * @param moshi 用于序列化的 Moshi 实例。
 * @return JSON 字符串；如果对象为 null 或序列化失败，则返回 null。
 */
inline fun <reified T> T?.toJsonString(moshi: Moshi = DefaultMoshi): String? {
    if (this == null) return null
    return try {
        val adapter: JsonAdapter<T> = moshi.adapter(T::class.java)
        adapter.toJson(this)
    } catch (e: Exception) {
        // 可以在这里添加日志记录 e.printStackTrace()
        logE(message = "Json Fail", throwable = e)
        null
    }
}

/**
 * 将 JSON 字符串转换为指定类型（T）的对象。
 * @param T 目标对象的类型。
 * @param moshi 用于反序列化的 Moshi 实例。
 * @return 类型为 T 的对象；如果 JSON 字符串为空或反序列化失败，则返回 null。
 */
inline fun <reified T> String?.toObject(moshi: Moshi = DefaultMoshi): T? {
    if (this.isNullOrBlank()) return null
    return try {
        val adapter: JsonAdapter<T> = moshi.adapter(T::class.java)
        adapter.fromJson(this)
    } catch (e: Exception) {
        // 可以在这里添加日志记录 e.printStackTrace()
        logE(message = "Json Fail", throwable = e)
        null
    }
}

/**
 * 将 JSON 字符串（表示一个数组）转换为指定类型的 List。
 * @param T List 中元素的类型。
 * @param moshi 用于反序列化的 Moshi 实例。
 * @return `List<T>`；如果 JSON 字符串为空或反序列化失败，则返回 null。
 */
inline fun <reified T> String?.fromJsonToList(moshi: Moshi = DefaultMoshi): List<T>? {
    if (this.isNullOrBlank()) return null
    return try {
        val listType: Type = Types.newParameterizedType(List::class.java, T::class.java)
        val adapter: JsonAdapter<List<T>> = moshi.adapter(listType)
        adapter.fromJson(this)
    } catch (e: Exception) {
        // 可以在这里添加日志记录 e.printStackTrace()
        logE(message = "Json Fail", throwable = e)
        null
    }
}

/**
 * 将任意类型的 Map<K, V> 转换为 JSON 字符串。
 * @param K Map 中键（Key）的类型。
 * @param V Map 中值（Value）的类型。
 * @param moshi 用于序列化的 Moshi 实例。
 * @return JSON 字符串；如果 Map 为 null 或序列化失败，则返回 null。
 */
inline fun <reified K, reified V> Map<K, V>?.toJsonStringMap(moshi: Moshi = DefaultMoshi): String? {
    if (this == null) return null
    return try {
        // 对于泛型 Map，必须使用 Types.newParameterizedType 来构建准确的类型信息
        val mapType: Type = Types.newParameterizedType(Map::class.java, K::class.java, V::class.java)
        val adapter: JsonAdapter<Map<K, V>> = moshi.adapter(mapType)
        adapter.toJson(this)
    } catch (e: Exception) {
        // 可以在这里添加日志 e.printStackTrace()
        logE(message = "Json Fail", throwable = e)
        null
    }
}


/**
 * 将 JSON 字符串（表示一个对象）转换为指定类型的 Map。
 * @param K Map 中键（Key）的类型。
 * @param V Map 中值（Value）的类型。
 * @param moshi 用于反序列化的 Moshi 实例。
 * @return `Map<K, V>`；如果 JSON 字符串为空或反序列化失败，则返回 null。
 */
inline fun <reified K, reified V> String?.fromJsonToMap(moshi: Moshi = DefaultMoshi): Map<K, V>? {
    if (this.isNullOrBlank()) return null
    return try {
        val mapType: Type =
            Types.newParameterizedType(Map::class.java, K::class.java, V::class.java)
        val adapter: JsonAdapter<Map<K, V>> = moshi.adapter(mapType)
        adapter.fromJson(this)
    } catch (e: Exception) {
        // 可以在这里添加日志记录 e.printStackTrace()
        logE(message = "Json Fail", throwable = e)
        null
    }
}