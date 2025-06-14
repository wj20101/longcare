package com.ytone.longcare.di

import android.content.Context
import okhttp3.OkHttpClient

/**
 * 这是 Release 版本的扩展函数。
 * 在 release 构建中，我们不添加任何 mock 相关的拦截器，所以函数体为空。
 */
fun OkHttpClient.Builder.addFlavorInterceptors(context: Context): OkHttpClient.Builder {
    // 在 release 版本中，什么都不做
    return this
}