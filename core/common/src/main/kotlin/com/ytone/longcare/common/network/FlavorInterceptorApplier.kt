package com.ytone.longcare.common.network

import android.content.Context
import okhttp3.OkHttpClient

interface FlavorInterceptorApplier {
    fun apply(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder
}
