package com.ytone.longcare.common.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.data.userstorage.UserDataStoreRegistry
import com.ytone.longcare.data.userstorage.UserDatabaseFactory
import com.ytone.longcare.data.userstorage.UserNamespaceMetadataStore
import com.ytone.longcare.data.userstorage.UserNamespacePathsFactory
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemConfigManagerUserScopeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val scopes = mutableSetOf<UserScopeKey>()

    @After
    fun cleanUp() {
        scopes.forEach { scope ->
            pathsFactory.forScope(scope).also { paths ->
                context.deleteDatabase(paths.databaseFile.name)
                paths.dataStoreFile.delete()
                paths.namespaceRoot.deleteRecursively()
                paths.sessionRoot.parentFile?.deleteRecursively()
            }
        }
    }

    @Test
    fun `same config key remains isolated and relogin reloads only matching user`() = runTest {
        val api = mockk<LongCareApiService>()
        coEvery { api.getSystemConfig() } returns ApiResult.Failure(503, "offline")
        val fixture = fixture(backgroundScope, api)
        val scopeA = scope(1, 2, 3)
        val scopeB = scope(1, 2, 4)

        fixture.registry.open(scopeA, SessionEpoch(10))
        fixture.manager.saveSystemConfig(SystemConfigModel(companyName = "company-a"))
        fixture.registry.open(scopeB, SessionEpoch(11))
        assertNull(fixture.manager.getSystemConfig())
        fixture.manager.saveSystemConfig(SystemConfigModel(companyName = "company-b"))
        assertEquals("company-b", fixture.manager.getSystemConfig()?.companyName)

        fixture.registry.open(scopeA, SessionEpoch(12))
        assertNull(fixture.manager.getSystemConfig())
        assertEquals("company-a", fixture.manager.getCompanyName())
    }

    @Test
    fun `network failure for new user is empty and never falls back to previous user`() = runTest {
        val api = mockk<LongCareApiService>()
        coEvery { api.getSystemConfig() } returns ApiResult.Failure(503, "offline")
        val fixture = fixture(backgroundScope, api)
        val scopeA = scope(11, 12, 13)
        val scopeB = scope(11, 12, 14)

        fixture.registry.open(scopeA, SessionEpoch(20))
        fixture.manager.saveSystemConfig(SystemConfigModel(companyName = "private-a"))
        fixture.registry.open(scopeB, SessionEpoch(21))

        assertEquals("", fixture.manager.getCompanyName())
        assertNull(fixture.manager.getSystemConfig())
        assertFalse(fixture.manager.hasSystemConfig())
    }

    @Test
    fun `fresh network value persists in current namespace`() = runTest {
        val api = mockk<LongCareApiService>()
        coEvery { api.getSystemConfig() } returns ApiResult.Success(
            SystemConfigModel(companyName = "rehydrated")
        )
        val fixture = fixture(backgroundScope, api)
        val user = scope(21, 22, 23)

        fixture.registry.open(user, SessionEpoch(30))
        assertEquals("rehydrated", fixture.manager.getCompanyName())
        fixture.registry.open(user, SessionEpoch(31))
        coEvery { api.getSystemConfig() } returns ApiResult.Failure(503, "offline")

        assertEquals("rehydrated", fixture.manager.getCompanyName())
    }

    private fun fixture(
        applicationScope: kotlinx.coroutines.CoroutineScope,
        api: LongCareApiService,
    ): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(applicationScope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        return Fixture(
            registry = registry,
            manager = SystemConfigManager(
                applicationScope = applicationScope,
                moshi = Moshi.Builder().build(),
                apiService = api,
                storageRegistry = registry,
            ),
        )
    }

    private fun scope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val manager: SystemConfigManager,
    )
}
