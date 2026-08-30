package com.ytone.longcare.network.interceptor

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Provides an immediate, deterministic offline boundary for performance-only variants.
 *
 * Release keeps [com.ytone.longcare.BuildConfig.PROFILE_OFFLINE_MODE] false, allowing R8 to
 * remove this path. Performance variants enable it so fixture tokens never reach production APIs
 * and cannot be mistaken for an expired real session.
 */
internal class PerformanceOfflineInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        throw IOException(OFFLINE_REASON)
    }

    internal companion object {
        const val OFFLINE_REASON = "longcare-performance-offline"
    }
}
