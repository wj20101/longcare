package com.ytone.longcare.di

import javax.inject.Qualifier

/**
 * 一个 Hilt 限定符，用于标识腾讯人脸识别API的 Retrofit 实例。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TencentFaceRetrofit

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultOkHttpClient
