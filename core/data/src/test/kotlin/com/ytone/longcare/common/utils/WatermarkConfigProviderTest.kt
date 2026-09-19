package com.ytone.longcare.common.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.domain.system.WatermarkConfigProvider
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatermarkConfigProviderTest {
    private val api = mockk<LongCareApiService>()

    @Test fun `provider returns configured logo and caches the response`() = runTest {
        coEvery { api.getSystemConfig() } returns ApiResult.Success(SystemConfigModel(syLogoImg = "https://example.com/logo.png"))
        val provider: WatermarkConfigProvider = SystemConfigManager(
            RuntimeEnvironment.getApplication(), backgroundScope,
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build(), api,
        )
        assertEquals("https://example.com/logo.png", provider.getSyLogoImg())
        assertEquals("https://example.com/logo.png", provider.getSyLogoImg())
        coVerify(exactly = 1) { api.getSystemConfig() }
    }

    @Test fun `provider keeps empty logo as empty`() = runTest {
        coEvery { api.getSystemConfig() } returns ApiResult.Success(SystemConfigModel())
        val provider: WatermarkConfigProvider = SystemConfigManager(RuntimeEnvironment.getApplication(), backgroundScope, Moshi.Builder().add(KotlinJsonAdapterFactory()).build(), api)
        assertEquals("", provider.getSyLogoImg())
    }

    @Test fun `network exception falls back to empty logo`() = runTest {
        coEvery { api.getSystemConfig() } throws java.io.IOException("offline")
        val provider: WatermarkConfigProvider = SystemConfigManager(RuntimeEnvironment.getApplication(), backgroundScope, Moshi.Builder().add(KotlinJsonAdapterFactory()).build(), api)
        assertEquals("", provider.getSyLogoImg())
    }

    @Test fun `cancellation is not converted to empty logo`() = runTest {
        coEvery { api.getSystemConfig() } throws CancellationException("cancel")
        val provider: WatermarkConfigProvider = SystemConfigManager(RuntimeEnvironment.getApplication(), backgroundScope, Moshi.Builder().add(KotlinJsonAdapterFactory()).build(), api)
        try {
            provider.getSyLogoImg()
            fail("Cancellation expected")
        } catch (_: CancellationException) {
            coVerify(exactly = 1) { api.getSystemConfig() }
        }
    }
}
