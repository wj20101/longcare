package com.ytone.longcare.di

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.IOException

/**
 * 一个自定义的 Moshi JsonAdapter，用于处理 Kotlin 的 Unit 类型。
 * 它将 Unit 序列化为一个空的 JSON 对象 "{}",
 * 并在反序列化时安全地消耗掉 null 或任何值，返回 Unit。
 */
object UnitJsonAdapter : JsonAdapter<Unit>() {

    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): Unit? {
        // 当从 JSON 解析时，我们不关心具体的值是什么。
        // 可能是 null，也可能是 "{}"。
        // 我们只需安全地跳过这个值，然后返回 Kotlin 的 Unit 单例即可。
        reader.skipValue()
        return Unit
    }

    @Throws(IOException::class)
    override fun toJson(writer: JsonWriter, value: Unit?) {
        // 当将 Unit 序列化为 JSON 时，我们将其表示为一个空的 JSON 对象。
        // 这样可以表示“有一个值，但它没有内容”。
        writer.beginObject()
        writer.endObject()
    }
}