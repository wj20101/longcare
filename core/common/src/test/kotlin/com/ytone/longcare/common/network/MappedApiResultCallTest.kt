package com.ytone.longcare.common.network

import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import okhttp3.Protocol
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class MappedApiResultCallTest {
    @Test fun `late cancelled response never enters mapper and reports cancellation`() {
        val delegate = mockk<Call<String>>()
        val rawCallback = slot<Callback<String>>()
        var cancelled = false
        every { delegate.isCanceled } answers { cancelled }
        every { delegate.enqueue(capture(rawCallback)) } just Runs
        every { delegate.cancel() } answers { cancelled = true }
        var mapped = 0
        val call = MappedApiResultCall(delegate) { value, _ -> mapped++; ApiResult.Success(value) }
        val callback = mockk<Callback<ApiResult<String>>>(relaxed = true)
        call.enqueue(callback)
        call.cancel()
        rawCallback.captured.onResponse(delegate, Response.success("late"))
        assertEquals(0, mapped)
        verify(exactly = 1) { callback.onFailure(call, any<IOException>()) }
        verify(exactly = 0) { callback.onResponse(any(), any()) }
    }

    @Test fun `cancel between response checks reports failure rather than escaping callback`() {
        val delegate = mockk<Call<String>>()
        val rawCallback = slot<Callback<String>>()
        every { delegate.isCanceled } returnsMany listOf(false, true)
        every { delegate.enqueue(capture(rawCallback)) } just Runs
        val call = MappedApiResultCall<String, String>(delegate) { value, _ -> error("cancelled mapper must not run: $value") }
        val callback = mockk<Callback<ApiResult<String>>>(relaxed = true)
        call.enqueue(callback)
        rawCallback.captured.onResponse(delegate, Response.success("late"))
        verify(exactly = 1) { callback.onFailure(call, any<IOException>()) }
    }

    @Test fun `each clone uses its actual response request generation`() {
        val delegate = mockk<Call<String>>()
        val clone = mockk<Call<String>>()
        every { delegate.clone() } returns clone
        every { delegate.isCanceled } returns false
        every { clone.isCanceled } returns false
        fun response(generation: Long): Response<String> = Response.success("ok", okhttp3.Response.Builder()
            .request(Request.Builder().url("https://example.test/")
                .tag(RequestSessionGeneration::class.java, RequestSessionGeneration(generation)).build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").build())
        every { delegate.execute() } returns response(1L)
        every { clone.execute() } returns response(2L)
        val generations = mutableListOf<Long>()
        val call = MappedApiResultCall(delegate) { value, request ->
            generations += requireNotNull(request.tag(RequestSessionGeneration::class.java)).value
            ApiResult.Success(value)
        }
        call.execute()
        call.clone().execute()
        assertEquals(listOf(1L, 2L), generations)
    }
}
