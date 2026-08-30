package com.ytone.longcare.common.utils

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.data.userstorage.UserStorageUnavailableException
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.ThirdKeyReturnModel
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemConfigManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val apiService = mockk<LongCareApiService>()
    private val testDispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(SupervisorJob() + testDispatcher)

    private lateinit var manager: SystemConfigManager
    private lateinit var storageRegistry: UserStorageRegistry

    @Before
    fun setup() {
        coEvery { apiService.getSystemConfig() } returns
            ApiResult.Success(SystemConfigModel())
        val lease = UserStorageLease(
            scopeKey = UserScopeKey(companyId = 1, accountId = 2, userId = 3),
            sessionEpoch = SessionEpoch(10),
            generation = StorageGeneration(1),
        )
        val dataStore = PreferenceDataStoreFactory.create(scope = appScope) {
            temporaryFolder.root.resolve("system-config.preferences_pb")
        }
        storageRegistry = mockk<UserStorageRegistry>()
        io.mockk.every { storageRegistry.requireCurrentLease() } returns lease
        io.mockk.every { storageRegistry.dataStore(lease) } returns dataStore
        io.mockk.every { storageRegistry.requireValid(lease) } returns Unit
        manager = SystemConfigManager(
            applicationScope = appScope,
            moshi = DefaultMoshi,
            apiService = apiService,
            storageRegistry = storageRegistry,
        )
    }

    @After
    fun tearDown() {
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
    fun `refreshCompanyName should fetch latest config and update cache`() = runTest(testDispatcher) {
        coEvery { apiService.getSystemConfig() } returns
            ApiResult.Success(SystemConfigModel(companyName = "LongCare Updated"))

        val companyName = manager.refreshCompanyName()

        assertEquals("LongCare Updated", companyName)
        assertEquals("LongCare Updated", manager.getSystemConfig()?.companyName)
        coVerify(exactly = 1) { apiService.getSystemConfig() }
    }

    @Test
    fun `refreshCompanyName abandons response when lease was revoked during request`() =
        runTest(testDispatcher) {
            coEvery { apiService.getSystemConfig() } returns
                ApiResult.Failure(401, "expired")
            io.mockk.every { storageRegistry.requireValid(any()) } throws
                UserStorageUnavailableException("logged out")

            val companyName = manager.refreshCompanyName()

            assertNull(companyName)
            assertNull(manager.getSystemConfig())
            coVerify(exactly = 1) { apiService.getSystemConfig() }
        }

    @Test
    fun `getMaxServicePhotoCount should return config value`() = runTest(testDispatcher) {
        manager.saveSystemConfig(SystemConfigModel(maxImgNum = 9))

        assertEquals(9, manager.getMaxServicePhotoCount())
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
