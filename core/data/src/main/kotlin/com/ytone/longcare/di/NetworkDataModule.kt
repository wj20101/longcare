package com.ytone.longcare.di

import android.app.Application
import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.json.UnitJsonAdapter
import com.ytone.longcare.common.json.UriJsonAdapter
import com.ytone.longcare.common.network.ApiResultCallAdapterFactory
import com.ytone.longcare.common.network.FlavorInterceptorApplier
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.common.network.TencentApiResultCallAdapterFactory
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.network.interceptor.RequestCryptoProvider
import com.ytone.longcare.network.interceptor.RequestDeviceInfoProvider
import com.ytone.longcare.network.interceptor.RequestInterceptor
import com.ytone.longcare.network.interceptor.ResponseDecryptInterceptor
import com.ytone.longcare.network.processor.ResponseProcessor
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
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkDataModule {
    private const val TENCENT_FACE_BASE_URL = "https://kyc1.qcloud.com"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(Unit::class.java, UnitJsonAdapter)
            .add(UriJsonAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(
        runtimeConfigProvider: RuntimeConfigProvider,
    ): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor()
        if (runtimeConfigProvider.isDebug) {
            // BODY logging serializes large Base64 photos and floods logcat during camera flows.
            // Request/response lines and timing remain available without exposing payload data.
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BASIC
        } else {
            loggingInterceptor.level = HttpLoggingInterceptor.Level.NONE
        }
        return loggingInterceptor
    }

    @Provides
    @Singleton
    fun provideRequestInterceptor(
        sessionSecretProvider: SessionSecretProvider,
        runtimeConfigProvider: RuntimeConfigProvider,
        requestDeviceInfoProvider: RequestDeviceInfoProvider,
        requestCryptoProvider: RequestCryptoProvider,
        privacyConsentManager: PrivacyConsentManager,
    ): RequestInterceptor {
        return RequestInterceptor(
            sessionSecretProvider = sessionSecretProvider,
            runtimeConfigProvider = runtimeConfigProvider,
            requestDeviceInfoProvider = requestDeviceInfoProvider,
            requestCryptoProvider = requestCryptoProvider,
            privacyConsentManager = privacyConsentManager,
        )
    }

    @Provides
    @Singleton
    fun provideResponseDecryptInterceptor(
        processors: Set<@JvmSuppressWildcards ResponseProcessor>
    ): ResponseDecryptInterceptor {
        return ResponseDecryptInterceptor(processors)
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
        responseDecryptInterceptor: ResponseDecryptInterceptor,
        cache: Cache,
        flavorInterceptorApplier: FlavorInterceptorApplier,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(requestInterceptor)
            .addInterceptor(responseDecryptInterceptor)
            .addInterceptor(loggingInterceptor)
            .cache(cache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        return flavorInterceptorApplier.apply(builder, context).build()
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
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        runtimeConfigProvider: RuntimeConfigProvider,
        sessionInvalidationHandler: SessionInvalidationHandler,
    ): Retrofit {
        return Retrofit.Builder().baseUrl(runtimeConfigProvider.baseUrl).client(okHttpClient)
            .addCallAdapterFactory(ApiResultCallAdapterFactory(sessionInvalidationHandler))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
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
        @DefaultOkHttpClient okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(TENCENT_FACE_BASE_URL)
            .client(okHttpClient)
            .addCallAdapterFactory(TencentApiResultCallAdapterFactory())
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
