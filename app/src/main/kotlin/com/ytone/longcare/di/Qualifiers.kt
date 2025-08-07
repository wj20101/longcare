package com.ytone.longcare.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceIdStorage // 用于 DeviceUtils 存储设备ID的 SharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppPrefs // 用于 AppPrefs 存储设备ID的 SharedPreferences

/**
 * 一个注解，用于标记某个 Retrofit API 方法应使用 JSON (Moshi) 进行序列化/反序列化。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UseJson

/**
 * 一个注解，用于标记某个 Retrofit API 方法应使用 Protobuf (Wire) 进行序列化/反序列化。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UseProtobuf

/**
 * 一个 Hilt 限定符，用于标识腾讯人脸识别API的 Retrofit 实例。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TencentFaceRetrofit

/**
 * 一个 Hilt 限定符，用于标识应用级别的 DataStore<Preferences>。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OrderStorage

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultOkHttpClient