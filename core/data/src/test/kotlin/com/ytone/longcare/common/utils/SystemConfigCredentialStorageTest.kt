package com.ytone.longcare.common.utils

import android.content.Context
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.SystemConfigModel
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemConfigCredentialStorageTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("system_config_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().build()
    private val api = mockk<LongCareApiService>()
    private val config = SystemConfigModel(
        companyName = "Care",
        maxImgNum = 9,
        thirdKeyStr = """{"GaoDeMapApiKey":"map-key","TxFaceAppId":"app","TxFaceAppSecret":"sensitive-value","TxFaceAppLicence":"licence"}""",
    )

    @Test
    fun `save excludes secret from both disk and ordinary memory cache`() = runTest {
        val manager = SystemConfigManager(context, backgroundScope, moshi, api, com.ytone.longcare.data.repository.UserSessionTracker())
        manager.saveSystemConfig(config)

        assertFalse(preferences.getString("system_config", "")!!.contains("sensitive-value"))
        assertFalse(manager.getSystemConfig()!!.thirdKeyStr.contains("sensitive-value"))
        assertEquals("map-key", manager.getThirdKeySync()?.gaoDeMapApiKey)
        assertEquals("Care", manager.getSystemConfig()?.companyName)
    }

    @Test
    fun `legacy disk payload is scrubbed before it enters memory`() = runTest {
        preferences.edit().putString("system_config", moshi.adapter(SystemConfigModel::class.java).toJson(config)).commit()
        val manager = SystemConfigManager(context, backgroundScope, moshi, api, com.ytone.longcare.data.repository.UserSessionTracker())

        assertFalse(manager.getSystemConfig()!!.thirdKeyStr.contains("sensitive-value"))
        assertFalse(preferences.getString("system_config", "")!!.contains("sensitive-value"))
        assertEquals("map-key", manager.getThirdKeySync()?.gaoDeMapApiKey)
        assertEquals(9, manager.getMaxServicePhotoCount())
    }
    @Test
    fun `legacy malformed and escaped nested values never survive migration`() = runTest {
        val payloads = listOf(
            "{malformed-sensitive-value",
            "null",
            "{\"TxFaceAppSecret\":\"sensitive-value\",\"extra\":{\"copy\":\"sensitive-value\"}}",
            "{\"TxFaceApp\\u0053ecret\":\"sensitive-value\",\"GaoDeMapApiKey\":\"map-key\"}",
        )
        for (payload in payloads) {
            preferences.edit().putString("system_config", moshi.adapter(SystemConfigModel::class.java).toJson(config.copy(thirdKeyStr = payload))).commit()
            val manager = SystemConfigManager(context, backgroundScope, moshi, api, com.ytone.longcare.data.repository.UserSessionTracker())
            assertFalse(manager.getSystemConfig()!!.thirdKeyStr.contains("sensitive-value"))
            assertFalse(preferences.getString("system_config", "")!!.contains("sensitive-value"))
            assertEquals("Care", manager.getCompanyName())
            assertEquals(9, manager.getMaxServicePhotoCount())
        }
    }

    @Test
    fun `network lazy load and company refresh expose only public configuration`() = runTest {
        preferences.edit().clear().commit()
        io.mockk.coEvery { api.getSystemConfig() } returns com.ytone.longcare.model.result.ApiResult.Success(config)
        val manager = SystemConfigManager(context, backgroundScope, moshi, api, com.ytone.longcare.data.repository.UserSessionTracker())
        assertEquals("map-key", manager.getThirdKey()?.gaoDeMapApiKey)
        assertEquals("", manager.getThirdKey()?.txFaceAppSecret)
        assertEquals("Care", manager.refreshCompanyName())
        assertFalse(preferences.getString("system_config", "")!!.contains("sensitive-value"))
        io.mockk.coEvery { api.getSystemConfig() } returns com.ytone.longcare.model.result.ApiResult.Failure(500, "offline")
        org.junit.Assert.assertNull(manager.refreshCompanyName())
        assertEquals("Care", manager.getCompanyName())
    }

}
