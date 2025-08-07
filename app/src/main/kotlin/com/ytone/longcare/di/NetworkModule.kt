package com.ytone.longcare.di

import android.app.Application
import android.content.Context
import com.squareup.moshi.Moshi
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.common.utils.DefaultMoshi
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.network.interceptor.RequestInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.wire.WireConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return DefaultMoshi
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
    fun provideRequestInterceptor(
        userSessionRepository: UserSessionRepository, deviceUtils: DeviceUtils
    ): RequestInterceptor {
        return RequestInterceptor(userSessionRepository, deviceUtils)
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
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
        requestInterceptor: RequestInterceptor,
        cache: Cache
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(requestInterceptor) // 自定义参数加密逻辑
            .addInterceptor(loggingInterceptor) // 添加日志拦截器
            .cache(cache) // 设置缓存
            .connectTimeout(30, TimeUnit.SECONDS) // 连接超时时间
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时时间
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时时间
            .addFlavorInterceptors(context)
            .build()
    }

    @Provides
    @Singleton
    @DefaultOkHttpClient
    fun provideDefaultOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 连接超时时间
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时时间
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时时间
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient, moshi: Moshi // 注入 Moshi 实例
    ): Retrofit {
        // 1. 创建 Moshi 和 Wire 各自的转换器工厂
        val moshiConverterFactory = MoshiConverterFactory.create(moshi)
        val wireConverterFactory = WireConverterFactory.create()

        // 2. 创建我们的“调度中心”工厂
        val qualifiedTypeConverterFactory = QualifiedTypeConverterFactory(
            jsonFactory = moshiConverterFactory, protobufFactory = wireConverterFactory
        )

        // 3. 构建 Retrofit 实例，只添加我们的调度工厂
        return Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(okHttpClient)
            .addConverterFactory(qualifiedTypeConverterFactory)
            .build()
    }

    @Provides
    @Singleton
    fun provideMyApiService(retrofit: Retrofit): LongCareApiService {
        return retrofit.create(LongCareApiService::class.java)
    }
    
    @Provides
    @Singleton
    @TencentFaceRetrofit
    fun provideTencentFaceRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        // 为腾讯云API创建单独的Retrofit实例
        return Retrofit.Builder()
            .baseUrl("https://kyc1.qcloud.com")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideTencentFaceApiService(
        @TencentFaceRetrofit retrofit: Retrofit
    ): TencentFaceApiService {
        return retrofit.create(TencentFaceApiService::class.java)
    }
}