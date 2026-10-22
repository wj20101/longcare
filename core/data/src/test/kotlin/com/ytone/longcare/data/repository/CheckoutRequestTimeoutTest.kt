package com.ytone.longcare.data.repository

import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.json.UnitJsonAdapter
import com.ytone.longcare.common.network.ApiResultCallAdapterFactory
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.result.ApiResult
import io.mockk.mockk
import java.io.Closeable
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Real loopback HTTP/Retrofit, not a backend/device-network result or a virtual-time-only test. */
class CheckoutRequestTimeoutTest {
    @Test(timeout = 10_000) fun `CheckEndOrder slow drip reaches deadline and cancels actual call`() =
        assertSlowDripTimeout(submit = false)

    @Test(timeout = 10_000) fun `EndOrder slow drip reaches deadline without replay or success`() =
        assertSlowDripTimeout(submit = true)

    private fun assertSlowDripTimeout(submit: Boolean) = runBlocking {
        Fixture(timeoutMillis = 1_500).use { fixture ->
            val result = request(fixture.repository, submit)
            assertTrue(result is ApiResult.Exception)
            assertTrue((result as ApiResult.Exception).exception is SocketTimeoutException)
            assertEquals(0L, fixture.bodyStarted.count)
            assertTrue(fixture.cancelled.await(2, TimeUnit.SECONDS))
            assertTrue(requireNotNull(fixture.call.get()).isCanceled())
            assertEquals(expectedPath(submit), fixture.server.takeRequest(2, TimeUnit.SECONDS)?.url?.encodedPath)
            assertNull(fixture.server.takeRequest(200, TimeUnit.MILLISECONDS))
            assertEquals(1, fixture.server.requestCount)
        }
    }

    @Test(timeout = 10_000) fun `parent cancellation propagates and cancels both checkout calls`() = runBlocking {
        for (submit in listOf(false, true)) {
            Fixture(timeoutMillis = 5_000).use { fixture ->
                val result = async(Dispatchers.Default) { request(fixture.repository, submit) }
                assertEquals(expectedPath(submit), fixture.server.takeRequest(2, TimeUnit.SECONDS)?.url?.encodedPath)
                assertTrue(fixture.bodyStarted.await(2, TimeUnit.SECONDS))
                result.cancelAndJoin()
                assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)
                assertTrue(fixture.cancelled.await(2, TimeUnit.SECONDS))
                assertTrue(requireNotNull(fixture.call.get()).isCanceled())
                assertNull(fixture.server.takeRequest(200, TimeUnit.MILLISECONDS))
                assertEquals(1, fixture.server.requestCount)
            }
        }
    }

    @Test(timeout = 10_000) fun `successful checkout responses remain unchanged without cancellation`() = runBlocking {
        for (submit in listOf(false, true)) {
            Fixture(timeoutMillis = null, slow = false).use { fixture ->
                val result = request(fixture.repository, submit)
                assertEquals(
                    if (submit) ApiResult.Success(com.ytone.longcare.model.EndOrderResultModel(1)) else ApiResult.Success(Unit),
                    result,
                )
                assertEquals(1L, fixture.cancelled.count)
                assertFalse(requireNotNull(fixture.call.get()).isCanceled())
                assertEquals(1, fixture.server.requestCount)
            }
        }
    }

    private suspend fun request(repository: OrderRepositoryImpl, submit: Boolean): ApiResult<*> =
        if (submit) repository.endOrder(1, "test-nfc", listOf(7), emptyList(), emptyList(), emptyList(), "1", "2", 1)
        else repository.checkEndOrder(1, listOf(7))

    private fun expectedPath(submit: Boolean) = if (submit) "/V1/Service/EndOrder" else "/V1/Service/CheckEndOrder"

    private class Fixture(timeoutMillis: Long?, slow: Boolean = true) : Closeable {
        val server = MockWebServer()
        val call = AtomicReference<Call>()
        val bodyStarted = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        private val client = OkHttpClient.Builder()
            // Bytes keep arriving well inside readTimeout, but the complete body takes > 6 seconds.
            .readTimeout(1, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun responseBodyStart(call: Call) { bodyStarted.countDown() }
                override fun canceled(call: Call) { cancelled.countDown() }
            }).build()
        val repository: OrderRepositoryImpl

        init {
            server.start()
            val response = MockResponse.Builder()
                .body("""{"resultCode":1000,"resultMsg":"ok","data":{"trueServiceTime":1},"padding":"${"x".repeat(16_384)}"}""")
            if (slow) response.throttleBody(64, 25, TimeUnit.MILLISECONDS)
            server.enqueue(response.build())
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .callFactory { request -> client.newCall(request).also { call.set(it) } }
                .addCallAdapterFactory(ApiResultCallAdapterFactory(mockk<SessionInvalidationHandler>(relaxed = true)))
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(Unit::class.java, UnitJsonAdapter).build()))
                .build().create(LongCareApiService::class.java)
            val details = mockk<OrderDetailRepository>()
            repository = if (timeoutMillis == null) OrderRepositoryImpl(api, details)
                else OrderRepositoryImpl(api, details, timeoutMillis)
        }

        override fun close() {
            client.dispatcher.cancelAll()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.close()
        }
    }
}
