package com.ytone.longcare.common.network

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.model.Response
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeApiCallTest {

    @Test
    fun `safeApiCall should emit ForceLogout on business code 3002`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventBus = mockk<AppEventBus>(relaxed = true)

        val result = safeApiCall(dispatcher, eventBus) {
            Response(
                resultCode = 3002,
                resultMsg = "login expired",
                data = null
            )
        }

        assertTrue(result is ApiResult.Failure)
        result as ApiResult.Failure
        assertEquals(3002, result.code)
        assertEquals("login expired", result.message)
        coVerify(exactly = 1) { eventBus.send(AppEvent.ForceLogout("login expired")) }
    }

    @Test
    fun `safeApiCall should rethrow CancellationException`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventBus = mockk<AppEventBus>(relaxed = true)

        var thrown: Throwable? = null
        try {
            safeApiCall<String>(dispatcher, eventBus) {
                throw CancellationException("cancel")
            }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is CancellationException)
    }
}
