package com.ytone.longcare.di

import android.content.Context
import com.ytone.longcare.network.interceptor.MockInterceptor
import okhttp3.OkHttpClient

/**
 * 这是 Debug 版本的扩展函数。
 * 它会将 MockInterceptor 添加到 OkHttpClient 的构建链中。
 */
fun OkHttpClient.Builder.addFlavorInterceptors(context: Context): OkHttpClient.Builder {
    return this.addInterceptor(MockInterceptor(context))
}