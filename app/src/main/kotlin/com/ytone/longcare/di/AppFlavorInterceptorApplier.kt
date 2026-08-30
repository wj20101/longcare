package com.ytone.longcare.di

import android.content.Context
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.network.FlavorInterceptorApplier
import com.ytone.longcare.network.interceptor.PerformanceOfflineInterceptor
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFlavorInterceptorApplier @Inject constructor() : FlavorInterceptorApplier {
    override fun apply(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder {
        return applySelectedFlavorNetworkBoundary(
            builder = builder,
            context = context,
            profileOfflineMode = BuildConfig.PROFILE_OFFLINE_MODE,
            applyBuildTypeInterceptors = { flavorContext ->
                addFlavorInterceptors(flavorContext)
            },
        )
    }
}

internal fun applySelectedFlavorNetworkBoundary(
    builder: OkHttpClient.Builder,
    context: Context,
    profileOfflineMode: Boolean,
    applyBuildTypeInterceptors: OkHttpClient.Builder.(Context) -> OkHttpClient.Builder,
): OkHttpClient.Builder {
    if (profileOfflineMode) {
        return builder.addInterceptor(PerformanceOfflineInterceptor())
    }
    return builder.applyBuildTypeInterceptors(context)
}
