package com.ytone.longcare.di

import android.content.Context
import com.ytone.longcare.common.network.FlavorInterceptorApplier
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFlavorInterceptorApplier @Inject constructor() : FlavorInterceptorApplier {
    override fun apply(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder {
        return builder.addFlavorInterceptors(context)
    }
}
