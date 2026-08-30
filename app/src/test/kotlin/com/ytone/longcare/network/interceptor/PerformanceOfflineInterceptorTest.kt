package com.ytone.longcare.network.interceptor

import io.mockk.mockk
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceOfflineInterceptorTest {
    @Test
    fun `interceptor fails immediately without touching production network`() {
        val error = runCatching {
            PerformanceOfflineInterceptor().intercept(mockk(relaxed = true))
        }.exceptionOrNull()

        require(error is IOException)
        assertEquals(PerformanceOfflineInterceptor.OFFLINE_REASON, error.message)
    }
}
