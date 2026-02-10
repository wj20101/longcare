package com.ytone.longcare.common.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.response.SystemConfigModel
import com.ytone.longcare.api.response.ThirdKeyReturnModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.model.Response
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemConfigManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiService = mockk<LongCareApiService>()
    private val eventBus = AppEventBus()
    private val testDispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + testDispatcher)

    private lateinit var context: Context
    private lateinit var manager: SystemConfigManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        coEvery { apiService.getSystemConfig() } returns Response(
            resultCode = 1000,
            resultMsg = "ok",
            data = SystemConfigModel()
        )
        manager = SystemConfigManager(
            context = context,
            applicationScope = appScope,
            moshi = DefaultMoshi,
            apiService = apiService,
            ioDispatcher = testDispatcher,
            eventBus = eventBus
        )
        manager.clearSystemConfig()
    }

    @After
    fun tearDown() {
        if (this::manager.isInitialized) {
            manager.clearSystemConfig()
        }
        appScope.cancel()
    }

    @Test
    fun `getFaceVerificationConfig should return config when third key is valid`() = runTest(testDispatcher) {
        val third = ThirdKeyReturnModel(
            txFaceAppId = "appId",
            txFaceAppSecret = "secret",
            txFaceAppLicence = "licence"
        )
        val thirdJson = DefaultMoshi.adapter(ThirdKeyReturnModel::class.java).toJson(third)
        manager.saveSystemConfig(SystemConfigModel(thirdKeyStr = thirdJson))

        val config = manager.getFaceVerificationConfig()

        assertEquals("appId", config?.appId)
        assertEquals("secret", config?.secret)
        assertEquals("licence", config?.licence)
    }

    @Test
    fun `getFaceVerificationConfig should return null when third key has blank fields`() = runTest(testDispatcher) {
        val third = ThirdKeyReturnModel(
            txFaceAppId = "",
            txFaceAppSecret = "secret",
            txFaceAppLicence = "licence"
        )
        val thirdJson = DefaultMoshi.adapter(ThirdKeyReturnModel::class.java).toJson(third)
        manager.saveSystemConfig(SystemConfigModel(thirdKeyStr = thirdJson))

        val config = manager.getFaceVerificationConfig()

        assertNull(config)
    }

    @Test
    fun `getFaceVerificationConfig should return null when third key json is invalid`() = runTest(testDispatcher) {
        manager.saveSystemConfig(SystemConfigModel(thirdKeyStr = "{invalid json"))

        val config = manager.getFaceVerificationConfig()

        assertNull(config)
    }
}
