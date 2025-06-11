package com.ytone.longcare.di

import android.app.Application
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.utils.DefaultJson
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.network.interceptor.RequestInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideKotlinxSerializationJson(): Json {
        return DefaultJson
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor()
        // 在Debug模式下打印日志，Release模式下不打印
        if (BuildConfig.DEBUG) { // 你需要确保你的 build.gradle 中有 BuildConfig.DEBUG
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        } else {
            loggingInterceptor.level = HttpLoggingInterceptor.Level.NONE
        }
        return loggingInterceptor
    }

    @Provides
    @Singleton
    fun provideRequestInterceptor(deviceUtils: DeviceUtils): RequestInterceptor {
        return RequestInterceptor(deviceUtils)
    }

    @Provides
    @Singleton
    fun provideOkHttpCache(application: Application): Cache {
        val cacheSize = 10 * 1024 * 1024L // 10 MB
        val httpCacheDirectory = File(application.cacheDir, "http-cache")
        return Cache(httpCacheDirectory, cacheSize)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        requestInterceptor: RequestInterceptor,
        cache: Cache
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(requestInterceptor) // 如果需要，可以添加自定义的认证拦截器等
            .addInterceptor(loggingInterceptor) // 添加日志拦截器
            .cache(cache) // 设置缓存
            .connectTimeout(30, TimeUnit.SECONDS) // 连接超时时间
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时时间
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时时间
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        kotlinxJson: Json = DefaultJson
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(kotlinxJson.asConverterFactory(contentType)) // 使用 Kotlinx Serialization Converter
            .build()
    }

    @Provides
    @Singleton
    fun provideMyApiService(retrofit: Retrofit): LongCareApiService {
        return retrofit.create(LongCareApiService::class.java)
    }
}