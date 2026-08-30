package com.ytone.longcare.di

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.network.interceptor.PerformanceOfflineInterceptor
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Test

class AppFlavorInterceptorApplierTest {
    @Test
    fun `profile offline boundary wins and never installs debug mock routing`() {
        val builder = OkHttpClient.Builder()
        val existing = mockk<Interceptor>()
        val debugMockSentinel = mockk<Interceptor>()
        builder.addInterceptor(existing)
        var debugInstallerCalls = 0

        val result =
            applySelectedFlavorNetworkBoundary(
                builder = builder,
                context = mockk<Context>(relaxed = true),
                profileOfflineMode = true,
                applyBuildTypeInterceptors = {
                    debugInstallerCalls += 1
                    addInterceptor(debugMockSentinel)
                },
            ).build()

        assertThat(debugInstallerCalls).isEqualTo(0)
        assertThat(result.interceptors).containsExactly(
            existing,
            result.interceptors.filterIsInstance<PerformanceOfflineInterceptor>().single(),
        ).inOrder()
        assertThat(result.interceptors).doesNotContain(debugMockSentinel)
    }

    @Test
    fun `normal debug mode delegates to build type interceptor exactly once`() {
        val debugMockSentinel = mockk<Interceptor>()
        var debugInstallerCalls = 0

        val result =
            applySelectedFlavorNetworkBoundary(
                builder = OkHttpClient.Builder(),
                context = mockk<Context>(relaxed = true),
                profileOfflineMode = false,
                applyBuildTypeInterceptors = {
                    debugInstallerCalls += 1
                    addInterceptor(debugMockSentinel)
                },
            ).build()

        assertThat(debugInstallerCalls).isEqualTo(1)
        assertThat(result.interceptors).containsExactly(debugMockSentinel)
    }
}
