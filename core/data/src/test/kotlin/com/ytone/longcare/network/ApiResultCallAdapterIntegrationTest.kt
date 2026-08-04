package com.ytone.longcare.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.common.json.UnitJsonAdapter
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.ApiResultCallAdapterFactory
import com.ytone.longcare.common.network.SessionInvalidation
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.common.network.SuppressSessionInvalidation
import com.ytone.longcare.common.network.TencentApiResultCallAdapterFactory
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.UserLatentDetailModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.io.IOException
import java.net.SocketTimeoutException

class ApiResultCallAdapterIntegrationTest {

    @Test
    fun `standard adapter maps success and business failure`() = runTest {
        val handler = RecordingSessionInvalidationHandler()
        val api = createStandardApi(handler)

        assertEquals(ApiResult.Success("value"), api.success())
        assertEquals(
            ApiResult.Failure(code = 4001, message = "业务失败"),
            api.businessFailure(),
        )
        assertEquals(ApiResult.Success(Unit), api.unitSuccess())
    }

    @Test
    fun `standard adapter accepts nullable fields from customer detail contract`() =
        runTest {
            val api = createStandardApi(RecordingSessionInvalidationHandler())

            val result = api.nullableCustomerDetail()

            assertTrue(result is ApiResult.Success)
            val detail = (result as ApiResult.Success).data
            assertEquals(7, detail.id)
            assertEquals("测试客户", detail.userName)
            assertNull(detail.guardianName)
            assertNull(detail.checkTime)
            assertNull(detail.pgResult)
            assertEquals("资料待补充", detail.checkDesc)
            assertEquals(82, detail.pgScore)
        }

    @Test
    fun `standard adapter invalidates session for 1001 and 3002 except silent endpoint`() = runTest {
        val handler = RecordingSessionInvalidationHandler()
        val api = createStandardApi(handler)

        assertEquals(
            ApiResult.Failure(code = 1001, message = "登录状态无效"),
            api.invalidSession(),
        )
        assertEquals(listOf("登录状态无效"), handler.reasons)

        assertEquals(
            ApiResult.Failure(code = 3002, message = "登录过期"),
            api.expired(),
        )
        assertEquals(listOf("登录状态无效", "登录过期"), handler.reasons)

        assertEquals(
            ApiResult.Failure(code = 3002, message = "登录过期"),
            api.silentExpired(),
        )
        assertEquals(listOf("登录状态无效", "登录过期"), handler.reasons)
    }

    @Test
    fun `standard adapter maps http network timeout and malformed response errors`() = runTest {
        val api = createStandardApi(RecordingSessionInvalidationHandler())

        assertRequestError(
            result = api.httpError(),
            kind = ApiRequestException.Kind.HTTP,
            httpCode = 503,
        )
        assertRequestError(
            result = api.networkError(),
            kind = ApiRequestException.Kind.CONNECTION,
        )
        assertRequestError(
            result = api.timeout(),
            kind = ApiRequestException.Kind.TIMEOUT,
        )
        assertRequestError(
            result = api.malformed(),
            kind = ApiRequestException.Kind.INVALID_RESPONSE,
        )
        assertRequestError(
            result = api.emptyResponse(),
            kind = ApiRequestException.Kind.INVALID_RESPONSE,
        )
    }

    @Test
    fun `http 401 is mapped but is not assumed to be business code 3002`() = runTest {
        val handler = RecordingSessionInvalidationHandler()
        val api = createStandardApi(handler)

        assertRequestError(
            result = api.unauthorized(),
            kind = ApiRequestException.Kind.HTTP,
            httpCode = 401,
        )
        assertTrue(handler.reasons.isEmpty())
    }

    @Test
    fun `cancelled synchronous call is not converted to a normal API error`() {
        val api = createStandardApi(RecordingSessionInvalidationHandler())
        val call = api.cancellableCall()
        call.cancel()

        assertThrows(IOException::class.java) {
            call.execute()
        }
    }

    @Test
    fun `tencent adapter maps direct success and failure responses`() = runTest {
        val api = createTencentApi()

        val success = api.success()
        assertTrue(success is ApiResult.Success)
        assertEquals(
            "token-value",
            (success as ApiResult.Success).data.accessToken,
        )
        assertEquals("7200", success.data.expireIn)
        assertEquals(
            ApiResult.Failure(code = 1001, message = "签名无效"),
            api.failure(),
        )
    }

    private fun createStandardApi(
        handler: SessionInvalidationHandler,
    ): StandardAdapterTestApi =
        retrofitBuilder()
            .addCallAdapterFactory(ApiResultCallAdapterFactory(handler))
            .build()
            .create(StandardAdapterTestApi::class.java)

    private fun createTencentApi(): TencentAdapterTestApi =
        retrofitBuilder()
            .addCallAdapterFactory(TencentApiResultCallAdapterFactory())
            .build()
            .create(TencentAdapterTestApi::class.java)

    private fun retrofitBuilder(): Retrofit.Builder {
        val moshi =
            Moshi.Builder()
                .add(Unit::class.java, UnitJsonAdapter)
                .add(KotlinJsonAdapterFactory())
                .build()
        val client =
            OkHttpClient.Builder()
                .addInterceptor(FakeResponseInterceptor())
                .build()
        return Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
    }

    private fun assertRequestError(
        result: ApiResult<*>,
        kind: ApiRequestException.Kind,
        httpCode: Int? = null,
    ) {
        assertTrue(result is ApiResult.Exception)
        val exception = (result as ApiResult.Exception).exception
        assertTrue(exception is ApiRequestException)
        exception as ApiRequestException
        assertEquals(kind, exception.kind)
        assertEquals(httpCode, exception.httpCode)
    }
}

private interface StandardAdapterTestApi {
    @GET("success")
    suspend fun success(): ApiResult<String>

    @GET("nullable-customer-detail")
    suspend fun nullableCustomerDetail(): ApiResult<UserLatentDetailModel>

    @GET("success")
    fun cancellableCall(): Call<ApiResult<String>>

    @GET("business-failure")
    suspend fun businessFailure(): ApiResult<String>

    @GET("unit-success")
    suspend fun unitSuccess(): ApiResult<Unit>

    @GET("expired")
    suspend fun expired(): ApiResult<String>

    @GET("invalid-session")
    suspend fun invalidSession(): ApiResult<String>

    @SuppressSessionInvalidation
    @GET("silent-expired")
    suspend fun silentExpired(): ApiResult<Unit>

    @GET("http-error")
    suspend fun httpError(): ApiResult<String>

    @GET("network-error")
    suspend fun networkError(): ApiResult<String>

    @GET("timeout")
    suspend fun timeout(): ApiResult<String>

    @GET("malformed")
    suspend fun malformed(): ApiResult<String>

    @GET("empty-response")
    suspend fun emptyResponse(): ApiResult<String>

    @GET("unauthorized")
    suspend fun unauthorized(): ApiResult<String>
}

private interface TencentAdapterTestApi {
    @GET("tencent-success")
    suspend fun success(): ApiResult<TencentAccessTokenResponse>

    @GET("tencent-failure")
    suspend fun failure(): ApiResult<TencentAccessTokenResponse>
}

private class RecordingSessionInvalidationHandler : SessionInvalidationHandler {
    override val invalidations: StateFlow<SessionInvalidation?> =
        MutableStateFlow(null)
    val reasons = mutableListOf<String>()

    override fun invalidate(reason: String) {
        reasons += reason
    }

    override fun consume(id: Long) = Unit
}

private class FakeResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath.removePrefix("/")
        if (path == "network-error") {
            throw IOException("offline")
        }
        if (path == "timeout") {
            throw SocketTimeoutException("timeout")
        }

        val (code, body) =
            when (path) {
                "success" ->
                    200 to
                        """{"resultCode":1000,"resultMsg":"ok","data":"value"}"""

                "business-failure" ->
                    200 to
                        """{"resultCode":4001,"resultMsg":"业务失败","data":null}"""

                "unit-success" ->
                    200 to
                        """{"resultCode":1000,"resultMsg":"ok","data":{}}"""

                "nullable-customer-detail" ->
                    200 to
                        """
                        {
                          "resultCode":1000,
                          "resultMsg":"ok",
                          "data":{
                            "id":7,
                            "userName":"测试客户",
                            "identityCardNumber":null,
                            "guardianName":null,
                            "guardianPhone":null,
                            "guardianRelation":null,
                            "liveAddress":null,
                            "liveLng":null,
                            "liveLat":null,
                            "img1":null,
                            "img2":null,
                            "img3":null,
                            "checkStatus":0,
                            "checkTime":null,
                            "checkDesc":"资料待补充",
                            "createTime":null,
                            "pgId":0,
                            "pgResult":null,
                            "pgScore":82,
                            "pgUrl":null
                          }
                        }
                        """.trimIndent()

                "expired", "silent-expired" ->
                    200 to
                        """{"resultCode":3002,"resultMsg":"登录过期","data":null}"""

                "invalid-session" ->
                    200 to
                        """{"resultCode":1001,"resultMsg":"登录状态无效","data":null}"""

                "http-error" -> 503 to """{"message":"unavailable"}"""
                "unauthorized" -> 401 to """{"message":"unauthorized"}"""
                "malformed" -> 200 to "{"
                "empty-response" -> 204 to ""
                "tencent-success" ->
                    200 to
                        """
                        {
                          "code":"0",
                          "msg":"ok",
                          "transactionTime":"2026-07-28T10:00:00",
                          "access_token":"token-value",
                          "expire_in":7200
                        }
                        """.trimIndent()

                "tencent-failure" ->
                    200 to
                        """
                        {
                          "code":"1001",
                          "msg":"签名无效",
                          "transactionTime":"2026-07-28T10:00:00"
                        }
                        """.trimIndent()

                else -> error("Unexpected path: $path")
            }

        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Service Unavailable")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
