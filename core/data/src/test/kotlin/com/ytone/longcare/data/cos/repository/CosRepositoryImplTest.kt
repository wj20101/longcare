package com.ytone.longcare.data.cos.repository

import com.tencent.cos.xml.common.ClientErrorCode
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.CosStorageException
import com.ytone.longcare.model.CosStorageFailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `retryable COS client failure clears credentials and retries once`() = runTest {
        var attempts = 0
        var cacheClears = 0

        val value =
            executeCosOperationWithRetry(
                clearCache = { cacheClears += 1 },
                operation = {
                    attempts += 1
                    if (attempts == 1) {
                        throw CosXmlClientException(ClientErrorCode.POOR_NETWORK)
                    }
                    "uploaded"
                },
            )

        assertEquals("uploaded", value)
        assertEquals(2, attempts)
        assertEquals(1, cacheClears)
    }

    @Test
    fun `non-retryable COS failure is not retried`() = runTest {
        var attempts = 0
        var cacheClears = 0

        val failure =
            try {
                executeCosOperationWithRetry(
                    clearCache = { cacheClears += 1 },
                    operation = {
                        attempts += 1
                        throw CosXmlClientException(ClientErrorCode.INVALID_ARGUMENT)
                    },
                )
                null
            } catch (exception: CosStorageException) {
                exception
            }

        assertFalse(requireNotNull(failure).retryable)
        assertEquals(1, attempts)
        assertEquals(0, cacheClears)
    }

    @Test
    fun `COS 404 maps to not found instead of an existing object`() {
        val source =
            CosXmlServiceException("Not Found", "cos.test").apply {
                statusCode = 404
            }

        val failure = source.toCosStorageException()

        assertEquals(CosStorageFailureKind.NOT_FOUND, failure.kind)
        assertFalse(failure.retryable)
    }

    @Test
    fun `private COS URL must come from backend and may not be blank or fall back`() {
        assertEquals(
            "https://signed.example.test/object",
            ApiResult.Success(" https://signed.example.test/object ").requirePrivateCosUrl(),
        )

        val blankFailure =
            org.junit.Assert.assertThrows(CosStorageException::class.java) {
                ApiResult.Success(" ").requirePrivateCosUrl()
            }
        assertEquals(CosStorageFailureKind.INVALID_RESPONSE, blankFailure.kind)

        val backendFailure =
            org.junit.Assert.assertThrows(CosStorageException::class.java) {
                ApiResult.Failure(4001, "无权访问文件").requirePrivateCosUrl()
            }
        assertTrue(backendFailure.errorCode.contains("4001"))
    }
}
