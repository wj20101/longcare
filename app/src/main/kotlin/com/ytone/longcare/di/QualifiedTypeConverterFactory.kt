package com.ytone.longcare.di

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class QualifiedTypeConverterFactory(
    private val jsonFactory: Converter.Factory,
    private val protobufFactory: Converter.Factory
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        annotations.forEach { annotation ->
            when (annotation) {
                is UseJson -> return jsonFactory.responseBodyConverter(type, annotations, retrofit)
                is UseProtobuf -> return protobufFactory.responseBodyConverter(type, annotations, retrofit)
            }
        }
        // 默认回退到 JSON
        return jsonFactory.responseBodyConverter(type, annotations, retrofit)
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        methodAnnotations.forEach { annotation ->
            when (annotation) {
                is UseJson -> return jsonFactory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
                is UseProtobuf -> return protobufFactory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
            }
        }
        // 默认回退到 JSON
        return jsonFactory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
    }
}