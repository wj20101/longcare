package com.ytone.longcare.common.network

import com.ytone.longcare.api.response.TencentApiResponse
import com.ytone.longcare.common.event.AppEventBus
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeTencentApiCallTest {

    private data class FakeTencentResponse(
        override val code: String,
        override val msg: String,
        override val transactionTime: String = "2026-01-01T00:00:00Z"
    ) : TencentApiResponse

    @Test
    fun `safeTencentApiCall should return Success when code is 0`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventBus = mockk<AppEventBus>(relaxed = true)

        val result = safeTencentApiCall(dispatcher, eventBus) {
            FakeTencentResponse(code = "0", msg = "ok")
        }

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `safeTencentApiCall should return Failure when code is non-zero`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventBus = mockk<AppEventBus>(relaxed = true)

        val result = safeTencentApiCall(dispatcher, eventBus) {
            FakeTencentResponse(code = "10001", msg = "failed")
        }

        assertTrue(result is ApiResult.Failure)
        result as ApiResult.Failure
        assertEquals(10001, result.code)
        assertEquals("failed", result.message)
    }

    @Test
    fun `safeTencentApiCall should rethrow CancellationException`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventBus = mockk<AppEventBus>(relaxed = true)

        var thrown: Throwable? = null
        try {
            safeTencentApiCall(dispatcher, eventBus) {
                throw CancellationException("cancel")
            }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is CancellationException)
    }
}
