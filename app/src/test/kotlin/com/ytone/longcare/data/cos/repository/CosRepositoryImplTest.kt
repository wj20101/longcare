package com.ytone.longcare.data.cos.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CosRepositoryImplTest {

    @Test
    fun `resolveCredentialRefreshStrategy should skip and use cache on main thread when cached`() {
        val strategy = resolveCredentialRefreshStrategy(
            isMainThread = true,
            hasCachedConfig = true
        )

        assertEquals(CredentialRefreshStrategy.SKIP_USE_CACHE, strategy)
    }

    @Test
    fun `resolveCredentialRefreshStrategy should skip on main thread when no cache`() {
        val strategy = resolveCredentialRefreshStrategy(
            isMainThread = true,
            hasCachedConfig = false
        )

        assertEquals(CredentialRefreshStrategy.SKIP_NO_CACHE, strategy)
    }

    @Test
    fun `resolveCredentialRefreshStrategy should refresh synchronously off main thread`() {
        val strategy = resolveCredentialRefreshStrategy(
            isMainThread = false,
            hasCachedConfig = false
        )

        assertEquals(CredentialRefreshStrategy.REFRESH_SYNC, strategy)
    }

    @Test
    fun `runSyncCredentialRefresh should return success when refresh completes`() = runTest {
        val result = runSyncCredentialRefresh(timeoutMs = 100L) {
            // no-op
        }

        assertEquals(SyncCredentialRefreshResult.SUCCESS, result)
    }

    @Test
    fun `runSyncCredentialRefresh should return failed when refresh throws`() = runTest {
        val result = runSyncCredentialRefresh(timeoutMs = 100L) {
            error("refresh failed")
        }

        assertEquals(SyncCredentialRefreshResult.FAILED, result)
    }

    @Test
    fun `runSyncCredentialRefresh should return timed out when refresh exceeds timeout`() = runTest {
        val result = runSyncCredentialRefresh(timeoutMs = 50L) {
            delay(200L)
        }

        assertEquals(SyncCredentialRefreshResult.TIMED_OUT, result)
    }
}
